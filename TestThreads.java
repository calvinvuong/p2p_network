import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

public class TestThreads {
    String s = "hello";
    volatile AtomicBoolean flag = new AtomicBoolean(false);
    AtomicInteger count = new AtomicInteger(0);
    ArrayList<String> list;
    
    public static void main(String[] args) {
	TestThreads app = new TestThreads();
	app.runThreads();
    }

    public TestThreads() {
	list = new ArrayList<String>();
    }
    
    public void printList() {
	System.out.println(list);
    }

    public void runThreads() {
	System.out.println(flag);
	System.out.println(count);
	Thread t1 = new Thread(new MyThread("t1", "hobbit", list, flag, count));
	Thread t2 = new Thread(new MyThread("t2", "eisengard", list, flag, count));
	
	t1.start();
	t2.start();

	try {
	    t1.join();
	    t2.join();
	    printList();
	    System.out.println(flag);
	    System.out.println(count);
	}
	catch (InterruptedException e) {
	    e.printStackTrace();
	}
    }
}

class MyThread implements Runnable {
    private String name;
    private String add;
    private ArrayList<String> alter;
    private volatile AtomicBoolean flag;
    private AtomicInteger count;
    
    public MyThread(String n, String addition, ArrayList<String> input, AtomicBoolean inputFlag, AtomicInteger inputCount ) {
	name = n;
	add = addition;
	alter = input;	
	System.out.println("Starting: " + name);
	flag = inputFlag;
	count = inputCount;
    }

    public void run() {
	System.out.println("hi, my name is " + name);
	append();
	flag.set(true);
	count.incrementAndGet();
	//	System.out.println(alter);
		
    }

    public void append() {
	alter.add(add);
    }
	
}
