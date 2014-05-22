package crawler;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import test.Test;

public class UserCrawler implements Runnable {
	private static final String LOGIN_URL = "http://passport.cnblogs.com/login.aspx";
	private static final String USER_HOME = "http://home.cnblogs.com";
	// 页面cookie
	private static List<String> cookies;
	private static int c = 0;

	// 停止任务标志
	private static AtomicBoolean stop;
	// 当前爬虫的id
	private int id;
	// 用户缓存
	private UserBuffer mUserBuffer;
	// 日志与粉丝保存工具
	private Saver saver;

	static {
		stop = new AtomicBoolean(false);
		try {
			// 登录一次即可
			login();
			// 保存数据线程先启动
			Saver.getInstance().start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// new Thread(new CommandListener()).start();
	}

	public UserCrawler(UserBuffer userBuffer) {
		mUserBuffer = userBuffer;
		mUserBuffer.crawlerCountIncrease();
		id = c++;
		saver = Saver.getInstance();
	}

	@Override
	public void run() {
		if (id > 0) {
			// 等第一个线程启动一段时候再开始新的线程
			try {
				TimeUnit.SECONDS.sleep(20 + id);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("UserCrawler " + id + " start");
		int retry = 3;// 重置尝试次数
		while (stop.get() == false) {
			// 取出一个待访问
			String userId = mUserBuffer.pickOne();
			if (userId == null) {// 队列元素已经为空
				retry--;// 重试3次
				if (retry <= 0)
					break;
				else
					continue;
			}
			try {
				// 爬取粉丝
				List<String> fans = crawUser(userId, "/followers");
				// 爬取关注者
				List<String> heros = crawUser(userId, "/followees");
				// 只需要保持粉丝关系即可
				StringBuilder sb = new StringBuilder(userId).append("\t");
				for (String friend : fans) {
					sb.append(friend).append("\t");
				}
				sb.deleteCharAt(sb.length() - 1).append("\n");
				saver.save(sb.toString());
				// 被关注者应该放进队列里面，以供下次爬取他的粉丝
				fans.addAll(heros);
				mUserBuffer.addUnCrawedUsers(fans);
			} catch (Exception e) {
				saver.log(e.getMessage());
				// 访问错误时，放入访问出错的队列中，以备以后重新访问。
				mUserBuffer.addErrorUser(userId);
			}
		}
		System.out.println("UserCrawler " + id + " stop");
		// 当前线程停止了
		mUserBuffer.crawlerCountDecrease();
	}

	/**
	 * 爬取用户，根据tag来决定是爬该用户关注的人，还是该用户的粉丝
	 * 
	 * @param userId
	 * @return
	 * @throws IOException
	 */
	private List<String> crawUser(String userId, String tag) throws IOException {
		// 构造URL
		StringBuilder urlBuilder = new StringBuilder(USER_HOME);
		urlBuilder.append("/u/").append(userId).append(tag);
		// 请求页面
		String page = getPage(urlBuilder.toString());
		Document doc = Jsoup.parse(page);
		List<String> friends = new ArrayList<String>();
		// 爬取第一页
		friends.addAll(getOnePageFriends(doc));
		String nextUrl = null;
		// 不断地爬取下一页
		while ((nextUrl = getNextUrl(doc)) != null) {
			page = getPage(nextUrl);
			doc = Jsoup.parse(page);
			friends.addAll(getOnePageFriends(doc));
		}
		return friends;
	}

	/**
	 * 获取一页中的关注or粉丝
	 * 
	 * @param pageHtml
	 * @return
	 */

	private List<String> getOnePageFriends(Document doc) {
		List<String> firends = new ArrayList<String>();
		Elements inputElements = doc.getElementsByClass("avatar_name");
		for (Element inputElement : inputElements) {
			Elements links = inputElement.getElementsByTag("a");
			for (Element link : links) {
				// 从href中解析出用户id
				String href = link.attr("href");
				firends.add(href.substring(3, href.length() - 1));
			}
		}
		return firends;
	}

	/**
	 * 获取下一页的地址
	 * 
	 * @param doc
	 * @return
	 */
	private String getNextUrl(Document doc) {
		Elements inputElements = doc.getElementsByClass("pager");
		for (Element inputElement : inputElements) {
			Elements links = inputElement.getElementsByTag("a");
			for (Element link : links) {
				String text = link.text();
				if (text != null && text.contains("Next"))
					return USER_HOME + link.attr("href");
			}
		}
		return null;
	}

	/***
	 * 获取网页
	 * 
	 * @param pageUrl
	 * @return
	 * @throws IOException
	 */
	private static String getPage(String pageUrl) throws IOException {
		URL url = new URL(pageUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		if (cookies != null) {
			// 发送cookie信息上去，以表明自己的身份，否则会被认为没有权限
			for (String cookie : cookies) {
				conn.addRequestProperty("Cookie", cookie);
			}
		}
		conn.setRequestMethod("GET");
		conn.setUseCaches(false);
		// 设置超时时间为10秒
		conn.setReadTimeout(10000);
		conn.setRequestProperty("Charset", "UTF-8");
		conn.setRequestProperty("User-Agent", "Mozilla/5.0");
		conn.connect();
		InputStream urlStream = conn.getInputStream();
		BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(urlStream, "utf-8"));
		StringBuilder sb = new StringBuilder();
		String line = null;
		while ((line = bufferedReader.readLine()) != null) {
			sb.append(line);
		}
		bufferedReader.close();
		return sb.toString();
	}

	/***
	 * 终止所有爬虫任务
	 */
	public static void stop() {
		System.out.println("正在终止...");
		stop.compareAndSet(false, true);
		UserBuffer.getInstance().prepareForStop();
	}

	/**
	 * 使用Joup解析登录参数，然后POST发送参数实现登录
	 * 
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	private static void login() throws UnsupportedEncodingException,
			IOException {
		CookieHandler.setDefault(new CookieManager());
		// 获取登录页面
		String page = getPage(LOGIN_URL);
		// 从登录去取出参数，并填充账号和密码
		Document doc = Jsoup.parse(page);
		// 取登录表格
		Element loginform = doc.getElementById("frmLogin");
		Elements inputElements = loginform.getElementsByTag("input");
		List<String> paramList = new ArrayList<String>();
		for (Element inputElement : inputElements) {
			String key = inputElement.attr("name");
			String value = inputElement.attr("value");
			if (key.equals("tbUserName"))
				value = Test.Name;
			else if (key.equals("tbPassword"))
				value = Test.passwd;
			paramList.add(key + "=" + URLEncoder.encode(value, "UTF-8"));
		}
		// 封装请求参数
		StringBuilder para = new StringBuilder();
		for (String param : paramList) {
			if (para.length() == 0) {
				para.append(param);
			} else {
				para.append("&" + param);
			}
		}
		// POST发送登录
		String result = sendPost(LOGIN_URL, para.toString());
		if (!result.contains("followees")) {
			cookies = null;
			System.out.println("登录失败");
		} else
			System.out.println("登录成功");
	}

	/**
	 * Post发送数据，并返回结果页面
	 * 
	 * @param url
	 * @param postParams
	 * @return
	 * @throws Exception
	 */
	private static String sendPost(String url, String postParams)
			throws IOException {
		URL obj = new URL(url);
		HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
		conn.setUseCaches(false);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Charset", "UTF-8");
		conn.setRequestProperty("Host", "passport.cnblogs.com");
		conn.setRequestProperty("User-Agent", "Mozilla/5.0");
		conn.setRequestProperty("Connection", "keep-alive");
		conn.setRequestProperty("Referer", LOGIN_URL);
		conn.setRequestProperty("Content-Type",
				"application/x-www-form-urlencoded");
		conn.setRequestProperty("Content-Length",
				Integer.toString(postParams.length()));
		conn.setDoOutput(true);
		conn.setDoInput(true);
		DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
		wr.writeBytes(postParams);
		wr.flush();
		wr.close();
		List<String> co = conn.getHeaderFields().get("Set-Cookie");
		if (co != null)
			for (String c : co) {
				cookies.add(c.split(";", 1)[0]);
			}
		BufferedReader in = new BufferedReader(new InputStreamReader(
				conn.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		return response.toString();
	}

	/***
	 * 使用Socket实现多进程间的通信，用于终止爬虫
	 * 
	 * @author jiqunpeng
	 * 
	 *         创建时间：2014-5-18 下午2:37:44
	 */
	@SuppressWarnings("unused")
	private static class CommandListener3 implements Runnable {
		public static final int STOP_CODE = 19;
		public final static int port = 8790;

		@Override
		public void run() {
			try {
				System.out.println("CommandListener start");
				ServerSocket serverSocket = new ServerSocket(port);
				Socket socket = serverSocket.accept();
				InputStream iStream = socket.getInputStream();
				int code = iStream.read();
				if (code == STOP_CODE) {
					stop.compareAndSet(false, true);
					System.out
							.println("Get stop command, will stop in seconds.");
				}
				serverSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
