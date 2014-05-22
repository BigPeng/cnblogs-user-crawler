package crawler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;

/**
 * 用户缓存
 * 
 * @author jiqunpeng
 * 
 *         创建时间：2014-5-15 上午11:27:18
 */
public class UserBuffer {
	private static final String UNCRAWED_FILE = "unCrawedUsers.txt";
	private static final String ERROE_FILE = "errorUsers.txt";
	private static final String CRAWED_FILE = "crawedUsers.txt";
	// 已经访问的用户,包括访问成功和访问出错的用户
	private Set<String> crawedUsers;
	// 访问出错的用户
	private Set<String> errorUsers;
	// 未访问的用户
	private Queue<String> unCrawedUsers;
	// 爬虫计数器
	private int crawCount;
	// 计数器的锁
	private Object countLock;

	private static UserBuffer instance = new UserBuffer();

	public static UserBuffer getInstance() {
		return instance;
	}

	private UserBuffer() {
		crawedUsers = new HashSet<String>();// 已经访问的用户,包括访问成功和访问出错的用户
		errorUsers = new HashSet<String>();// 访问出错的用户
		unCrawedUsers = new LinkedList<String>();// 未访问的用户
	
		crawCount = 0;
		countLock = new Object();
		initCrawState();
	}
	/***
	 * 增加一个活动爬虫
	 */
	public void crawlerCountIncrease() {
		synchronized (countLock) {
			crawCount++;
		}
	}
	/***
	 *  减少一个活动爬虫，当活动爬虫数量为0时，触发状态保存
	 * @return
	 */
	public boolean crawlerCountDecrease() {
		synchronized (countLock) {
			crawCount--;
			if (crawCount <= 0) {
				try {
					saveCrawState();
					Saver.getInstance().stop();
					return false;
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
		return true;
	}

	/***
	 * 开启一个守护线程，用户强制关闭状态。crawlerCountDecrease()
	 * 在出现线程意外死掉的情况下，crawCount <=0
	 * 的条件不能触发保存状态；因此等待一段时间后，强制保存状态。
	 */
	public void prepareForStop() {
		Thread thread = new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					System.out.println("两分钟后，将强制保存状态");
					TimeUnit.SECONDS.sleep(120);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				//不断的减少计数，最终导致触发保存状态
				while (crawlerCountDecrease())
					;
			}

		});
		thread.setDaemon(true);
		thread.start();
	}

	private void initCrawState() {
		try {
			read(UNCRAWED_FILE, unCrawedUsers);
			System.out.println("上次队列中的用户数：" + unCrawedUsers.size());
			read(ERROE_FILE, errorUsers);
			System.out.println("上次出错的用户数：" + errorUsers.size());
			read(CRAWED_FILE, crawedUsers);
			System.out.println("上次爬过的用户数：" + crawedUsers.size());
		} catch (IOException e) {
			System.out.println("fisrt init");
			// 加入种子用户
			unCrawedUsers.add("fengfenggirl");
		}

	}

	private void read(String fileName, Collection<String> content)
			throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(
				new File(fileName)));
		String line;
		while ((line = in.readLine()) != null) {
			content.add(line);
		}
		in.close();
	}

	/**
	 * 保存爬取状态,记录那些拥有已经爬过,哪些还没有
	 * 
	 * @throws FileNotFoundException
	 */
	private void saveCrawState() throws FileNotFoundException {
		save(UNCRAWED_FILE, unCrawedUsers);
		save(ERROE_FILE, errorUsers);
		save(CRAWED_FILE, crawedUsers);
	}

	/**
	 * 保存content内容到fileName文件
	 * 
	 * @param fileName
	 * @param content
	 * @throws FileNotFoundException
	 */
	private void save(String fileName, Collection<String> content)
			throws FileNotFoundException {
		PrintWriter out = new PrintWriter(new File(fileName));
		for (String ids : content) {
			out.print(ids);
			out.print("\n");
		}
		out.flush();
		out.close();
	}

	/***
	 * 添加未访问的用户 
	 * 
	 * @param users
	 * @return
	 */
	public synchronized void addUnCrawedUsers(List<String> users) {
		// 添加未访问的用户
		for (String user : users) {
			if (!crawedUsers.contains(user))
				unCrawedUsers.add(user);
		}
	}

	/**
	 * 从队列中取一个元素,并把这个元素添加到已经访问的集合中，以免重复访问。
	 * 
	 * 
	 * @return
	 */
	public synchronized String pickOne() {
		String newId = unCrawedUsers.poll();
		// 队列中可能包含重复的id，因为插入队列时只检查是否在访问集合里，
		// 没有检查是否已经出现在队列里
		while (crawedUsers.contains(newId)) {
			newId = unCrawedUsers.poll();
		}
		//访问前先把添加到已经访问的集合中
		crawedUsers.add(newId);
		return newId;
	}

	/**
	 * 添加访问出错的用户
	 * 
	 * @param userId
	 */
	public synchronized void addErrorUser(String userId) {
		errorUsers.add(userId);
	}

}
