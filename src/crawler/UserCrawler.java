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
	// ҳ��cookie
	private static List<String> cookies;
	private static int c = 0;

	// ֹͣ�����־
	private static AtomicBoolean stop;
	// ��ǰ�����id
	private int id;
	// �û�����
	private UserBuffer mUserBuffer;
	// ��־���˿���湤��
	private Saver saver;

	static {
		stop = new AtomicBoolean(false);
		try {
			// ��¼һ�μ���
			login();
			// ���������߳�������
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
			// �ȵ�һ���߳�����һ��ʱ���ٿ�ʼ�µ��߳�
			try {
				TimeUnit.SECONDS.sleep(20 + id);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("UserCrawler " + id + " start");
		int retry = 3;// ���ó��Դ���
		while (stop.get() == false) {
			// ȡ��һ��������
			String userId = mUserBuffer.pickOne();
			if (userId == null) {// ����Ԫ���Ѿ�Ϊ��
				retry--;// ����3��
				if (retry <= 0)
					break;
				else
					continue;
			}
			try {
				// ��ȡ��˿
				List<String> fans = crawUser(userId, "/followers");
				// ��ȡ��ע��
				List<String> heros = crawUser(userId, "/followees");
				// ֻ��Ҫ���ַ�˿��ϵ����
				StringBuilder sb = new StringBuilder(userId).append("\t");
				for (String friend : fans) {
					sb.append(friend).append("\t");
				}
				sb.deleteCharAt(sb.length() - 1).append("\n");
				saver.save(sb.toString());
				// ����ע��Ӧ�÷Ž��������棬�Թ��´���ȡ���ķ�˿
				fans.addAll(heros);
				mUserBuffer.addUnCrawedUsers(fans);
			} catch (Exception e) {
				saver.log(e.getMessage());
				// ���ʴ���ʱ��������ʳ���Ķ����У��Ա��Ժ����·��ʡ�
				mUserBuffer.addErrorUser(userId);
			}
		}
		System.out.println("UserCrawler " + id + " stop");
		// ��ǰ�߳�ֹͣ��
		mUserBuffer.crawlerCountDecrease();
	}

	/**
	 * ��ȡ�û�������tag�������������û���ע���ˣ����Ǹ��û��ķ�˿
	 * 
	 * @param userId
	 * @return
	 * @throws IOException
	 */
	private List<String> crawUser(String userId, String tag) throws IOException {
		// ����URL
		StringBuilder urlBuilder = new StringBuilder(USER_HOME);
		urlBuilder.append("/u/").append(userId).append(tag);
		// ����ҳ��
		String page = getPage(urlBuilder.toString());
		Document doc = Jsoup.parse(page);
		List<String> friends = new ArrayList<String>();
		// ��ȡ��һҳ
		friends.addAll(getOnePageFriends(doc));
		String nextUrl = null;
		// ���ϵ���ȡ��һҳ
		while ((nextUrl = getNextUrl(doc)) != null) {
			page = getPage(nextUrl);
			doc = Jsoup.parse(page);
			friends.addAll(getOnePageFriends(doc));
		}
		return friends;
	}

	/**
	 * ��ȡһҳ�еĹ�עor��˿
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
				// ��href�н������û�id
				String href = link.attr("href");
				firends.add(href.substring(3, href.length() - 1));
			}
		}
		return firends;
	}

	/**
	 * ��ȡ��һҳ�ĵ�ַ
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
	 * ��ȡ��ҳ
	 * 
	 * @param pageUrl
	 * @return
	 * @throws IOException
	 */
	private static String getPage(String pageUrl) throws IOException {
		URL url = new URL(pageUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		if (cookies != null) {
			// ����cookie��Ϣ��ȥ���Ա����Լ�����ݣ�����ᱻ��Ϊû��Ȩ��
			for (String cookie : cookies) {
				conn.addRequestProperty("Cookie", cookie);
			}
		}
		conn.setRequestMethod("GET");
		conn.setUseCaches(false);
		// ���ó�ʱʱ��Ϊ10��
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
	 * ��ֹ������������
	 */
	public static void stop() {
		System.out.println("������ֹ...");
		stop.compareAndSet(false, true);
		UserBuffer.getInstance().prepareForStop();
	}

	/**
	 * ʹ��Joup������¼������Ȼ��POST���Ͳ���ʵ�ֵ�¼
	 * 
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	private static void login() throws UnsupportedEncodingException,
			IOException {
		CookieHandler.setDefault(new CookieManager());
		// ��ȡ��¼ҳ��
		String page = getPage(LOGIN_URL);
		// �ӵ�¼ȥȡ��������������˺ź�����
		Document doc = Jsoup.parse(page);
		// ȡ��¼���
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
		// ��װ�������
		StringBuilder para = new StringBuilder();
		for (String param : paramList) {
			if (para.length() == 0) {
				para.append(param);
			} else {
				para.append("&" + param);
			}
		}
		// POST���͵�¼
		String result = sendPost(LOGIN_URL, para.toString());
		if (!result.contains("followees")) {
			cookies = null;
			System.out.println("��¼ʧ��");
		} else
			System.out.println("��¼�ɹ�");
	}

	/**
	 * Post�������ݣ������ؽ��ҳ��
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
	 * ʹ��Socketʵ�ֶ���̼��ͨ�ţ�������ֹ����
	 * 
	 * @author jiqunpeng
	 * 
	 *         ����ʱ�䣺2014-5-18 ����2:37:44
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
