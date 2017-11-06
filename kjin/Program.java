// Place this in a directory called kjin
// Usage: javac kjin/Program.java && java kjin.Program

package kjin;

import java.lang.*;
import java.io.*;
import java.util.*;

public class Program {  
  /**
   * Gets an InputStream that randomly outputs bytes corresponding
   * to one of 'a' through 'e', or whitespace.
   * After 64 bytes, the stream ends.
   */
  private static InputStream getInputStream() {
    return new InputStream() {
      private char[] chars = {' ', 'a', 'b', 'c', 'd', 'e'};
      private int bytesLeft = 1 << 6;

      @Override
      public int read() throws IOException {
        if (bytesLeft == 0) {
          return -1;
        }
        bytesLeft--;
        try {
          Thread.sleep((long)(Math.random() * 100));
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        return chars[(int)Math.floor(Math.random() * chars.length)];
      }
    };
  }

  /**
   * Gets a Runnable object which tells a thread how to behave like a consumer.
   */
  private static Runnable getConsumerRunnable(int threadNum) {
    return new Runnable() {
      @Override
      public void run() {
        try {
          while (true) {
            // We wrap usage of the shared array in a synchronized block, which
            // indicates that anything that happens within it should be "atomic"
            // (cannot be split when the current thread is switched -- in some online
            // literature the act of switching out of a thread is called "preemption").
            // You can think of this as putting a "lock" on sharedArray, preventing
            // others from using it.
            synchronized (sharedArray) {
              // Even though we wait until a producer has added an element, we still want
              // to have a while loop here. This guarantees that the shared array will have size
              // 1 or more when we call get(0).
              while (sharedArray.size() == 0) {
                System.out.println("Thread #" + threadNum + " went to sleep");
                // Calling wait() blocks until someone calls notify() or notifyAll().
                // This releases the "lock", and then re-acquires it once woken up.
                sharedArray.wait();
                System.out.println("Thread #" + threadNum + " woke up");
              }
              String word = sharedArray.get(0);
              if (word.equals(SENTINEL)) {
                break;
              }
              for (int i = 0; i < WORDS.length; i++) {
                if (WORDS[i].equals(word)) {
                  System.out.println("Thread #" + threadNum + " detected word: " + word);
                  break;
                }
              }
              sharedArray.remove(0);
            }
          }
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    };
  }

  /**
   * Gets a Runnable object which tells a thread how to behave like a producer.
   * TODO: There may be issues running multiple producers at the same time, because
   * InputStream objects might not be synchronized.
   */
  private static Runnable getProducerRunnable(int threadNum) {
    return new Runnable() {
      @Override
      public void run() {
        try {
          String s = "";
          int a;
          // Read bytes as letters into a string until we see a space. Then add the string
          // to the shared array and start over. End when there are no more bytes to read.
          while (true) {
            a = inputStream.read();
            if (a == -1) {
              // See the consumer above for description of the synchronized keyword.
              // notify() and notifyAll() must also only appear in synchronized blocks.
              synchronized (sharedArray) {
                sharedArray.add(s);
                sharedArray.add(SENTINEL);
                // notifyAll() wakes up ALL threads that are blocked on wait().
                // We want all threads to finish when they get the sentinel value.
                sharedArray.notifyAll();
              }
              System.out.println("Thread #" + threadNum + " pushed " + s);
              System.out.println("Thread #" + threadNum + " ended");
              break;
            }
            
            if (a == ' ') {
              synchronized (sharedArray) {
                sharedArray.add(s);
                // notify() wakes up 1 thread that is blocked on wait().
                // Only one thread can respond to a new element at a time, so there
                // is no point in waking up everyone.
                // Warning -- If unsure whether you should use notify() or notifyAll(),
                // use notifyAll().
                sharedArray.notify();
              }
              System.out.println("Thread #" + threadNum + " pushed " + s);
              s = "";
            } else {
              s += (char)a;
            }
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    };
  }

  // Value sent by producer to signal that there are no more words to consume.
  private static final String[] WORDS = {
    "a", "ab", "ad", "add", "added",
    "bad", "bed", "bedded",
    "cab", "cede", "ceded",
    "dab", "dabbed", "dad", "dead", "deed",
    "ebb", "ebbed"
  };
  private static final String SENTINEL = "END";
  private static InputStream inputStream;
  private static List<String> sharedArray;

  public static void main(String[] args) {
    inputStream = getInputStream();
    // synchronizedList is needed to access sharedArray on multiple threads.
    sharedArray = Collections.synchronizedList(new LinkedList<>());

    Thread producerThread = new Thread(getProducerRunnable(1));
    Thread consumerThread1 = new Thread(getConsumerRunnable(2));
    Thread consumerThread2 = new Thread(getConsumerRunnable(3));
    producerThread.start();
    consumerThread1.start();
    consumerThread2.start();
    try {
      // join() blocks until the thread has finished executing.
      producerThread.join();
      consumerThread1.join();
      consumerThread2.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.println("We're done!");
  }
}
