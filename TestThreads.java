import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class TestThreads {
    String s = "hello";
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
	Thread t1 = new Thread(new MyThread("t1", "hobbit", list));
	Thread t2 = new Thread(new MyThread("t2", "eisengard", list));
	
	t1.start();
	t2.start();

	try {
	    t1.join();
	    t2.join();
	    printList();
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
    
    public MyThread(String n, String addition, ArrayList<String> input) {
	name = n;
	add = addition;
	alter = input;	
	System.out.println("Starting: " + name);
    }

    public void run() {
	System.out.println("hi, my name is " + name);
	append();
	//	System.out.println(alter);
		
    }

    public void append() {
	alter.add(add);
    }
	
}
