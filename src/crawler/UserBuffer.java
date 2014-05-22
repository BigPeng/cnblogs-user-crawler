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
 * �û�����
 * 
 * @author jiqunpeng
 * 
 *         ����ʱ�䣺2014-5-15 ����11:27:18
 */
public class UserBuffer {
	private static final String UNCRAWED_FILE = "unCrawedUsers.txt";
	private static final String ERROE_FILE = "errorUsers.txt";
	private static final String CRAWED_FILE = "crawedUsers.txt";
	// �Ѿ����ʵ��û�,�������ʳɹ��ͷ��ʳ�����û�
	private Set<String> crawedUsers;
	// ���ʳ�����û�
	private Set<String> errorUsers;
	// δ���ʵ��û�
	private Queue<String> unCrawedUsers;
	// ���������
	private int crawCount;
	// ����������
	private Object countLock;

	private static UserBuffer instance = new UserBuffer();

	public static UserBuffer getInstance() {
		return instance;
	}

	private UserBuffer() {
		crawedUsers = new HashSet<String>();// �Ѿ����ʵ��û�,�������ʳɹ��ͷ��ʳ�����û�
		errorUsers = new HashSet<String>();// ���ʳ�����û�
		unCrawedUsers = new LinkedList<String>();// δ���ʵ��û�
	
		crawCount = 0;
		countLock = new Object();
		initCrawState();
	}
	/***
	 * ����һ�������
	 */
	public void crawlerCountIncrease() {
		synchronized (countLock) {
			crawCount++;
		}
	}
	/***
	 *  ����һ������棬�����������Ϊ0ʱ������״̬����
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
	 * ����һ���ػ��̣߳��û�ǿ�ƹر�״̬��crawlerCountDecrease()
	 * �ڳ����߳���������������£�crawCount <=0
	 * ���������ܴ�������״̬����˵ȴ�һ��ʱ���ǿ�Ʊ���״̬��
	 */
	public void prepareForStop() {
		Thread thread = new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					System.out.println("�����Ӻ󣬽�ǿ�Ʊ���״̬");
					TimeUnit.SECONDS.sleep(120);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				//���ϵļ��ټ��������յ��´�������״̬
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
			System.out.println("�ϴζ����е��û�����" + unCrawedUsers.size());
			read(ERROE_FILE, errorUsers);
			System.out.println("�ϴγ�����û�����" + errorUsers.size());
			read(CRAWED_FILE, crawedUsers);
			System.out.println("�ϴ��������û�����" + crawedUsers.size());
		} catch (IOException e) {
			System.out.println("fisrt init");
			// ���������û�
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
	 * ������ȡ״̬,��¼��Щӵ���Ѿ�����,��Щ��û��
	 * 
	 * @throws FileNotFoundException
	 */
	private void saveCrawState() throws FileNotFoundException {
		save(UNCRAWED_FILE, unCrawedUsers);
		save(ERROE_FILE, errorUsers);
		save(CRAWED_FILE, crawedUsers);
	}

	/**
	 * ����content���ݵ�fileName�ļ�
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
	 * ���δ���ʵ��û� 
	 * 
	 * @param users
	 * @return
	 */
	public synchronized void addUnCrawedUsers(List<String> users) {
		// ���δ���ʵ��û�
		for (String user : users) {
			if (!crawedUsers.contains(user))
				unCrawedUsers.add(user);
		}
	}

	/**
	 * �Ӷ�����ȡһ��Ԫ��,�������Ԫ����ӵ��Ѿ����ʵļ����У������ظ����ʡ�
	 * 
	 * 
	 * @return
	 */
	public synchronized String pickOne() {
		String newId = unCrawedUsers.poll();
		// �����п��ܰ����ظ���id����Ϊ�������ʱֻ����Ƿ��ڷ��ʼ����
		// û�м���Ƿ��Ѿ������ڶ�����
		while (crawedUsers.contains(newId)) {
			newId = unCrawedUsers.poll();
		}
		//����ǰ�Ȱ���ӵ��Ѿ����ʵļ�����
		crawedUsers.add(newId);
		return newId;
	}

	/**
	 * ��ӷ��ʳ�����û�
	 * 
	 * @param userId
	 */
	public synchronized void addErrorUser(String userId) {
		errorUsers.add(userId);
	}

}
