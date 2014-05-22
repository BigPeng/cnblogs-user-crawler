package crawler;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Crawler {
	public static void main(String[] args) {
		ExecutorService exec = Executors.newCachedThreadPool();
		UserBuffer buffer = UserBuffer.getInstance();
		for (int i = 0; i < 10; i++) {
			UserCrawler uc = new UserCrawler(buffer);
			exec.execute(uc);			
		}
		exec.execute(new Lisenter());
		exec.shutdown();
	}

	static class Lisenter implements Runnable {

		@Override
		public void run() {
			System.out.println("ÊäÈë'='½áÊø..");
			while (true) {
				try {
					int a = System.in.read();
					if (a == '=') {
						UserCrawler.stop();
						break;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			System.out.println("Lisenter stop");
		}

	}

}
