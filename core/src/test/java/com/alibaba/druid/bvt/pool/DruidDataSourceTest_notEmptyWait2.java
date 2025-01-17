package com.alibaba.druid.bvt.pool;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import junit.framework.TestCase;

import org.junit.Assert;

import com.alibaba.druid.pool.DruidDataSource;

public class DruidDataSourceTest_notEmptyWait2 extends TestCase {
    private DruidDataSource dataSource;

    protected void setUp() throws Exception {
        dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:mock:xxx");
        dataSource.setTestOnBorrow(false);
        dataSource.setMaxActive(1);

        dataSource.setMaxWaitThreadCount(10);
    }

    protected void tearDown() throws Exception {
        dataSource.close();
    }

    public void test_maxWaitThread() throws Exception {
        {
            Connection conn = dataSource.getConnection();
            conn.close();
        }

        Connection conn = dataSource.getConnection();
        final AtomicLong errorCount = new AtomicLong();

        final int THREAD_COUNT = 10;
        final CountDownLatch startLatch = new CountDownLatch(THREAD_COUNT);
        final CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        Thread[] threads = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; ++i) {
            threads[i] = new Thread("thread-" + i) {
                public void run() {
                    startLatch.countDown();
                    try {
                        System.out.println(Thread.currentThread() +" "+ LocalDateTime.now() + " begin " );
                        Connection conn = dataSource.getConnection();
                        Thread.sleep(2);
                        System.out.println(Thread.currentThread() +" "+ LocalDateTime.now() + " getConnection== " + conn);
                        conn.close();
                    } catch (Exception e) {
                        // e.printStackTrace();
                        errorCount.incrementAndGet();
                    } finally {
                        endLatch.countDown();
                    }
                }
            };
            threads[i].start();
        }

        startLatch.await(100, TimeUnit.MILLISECONDS);

        final CountDownLatch errorThreadEndLatch = new CountDownLatch(THREAD_COUNT);
        final AtomicLong maxWaitErrorCount = new AtomicLong();
        Thread errorThread = new Thread() {
            public void run() {
                try {
                    Connection conn = dataSource.getConnection();
                    Thread.sleep(1);
                    conn.close();
                } catch (Exception e) {
                    maxWaitErrorCount.incrementAndGet();
                } finally {
                    errorThreadEndLatch.countDown();
                }
            }
        };
        errorThread.start();

        errorThreadEndLatch.await(100, TimeUnit.MILLISECONDS);

        Assert.assertEquals(0, maxWaitErrorCount.get());//因为最大超时没有设置，所以线程一直循环进不到maxWaitErrorCount加1的逻辑了
        Assert.assertTrue(dataSource.getNotEmptySignalCount() > 0);

        conn.close();

        System.out.println(Thread.currentThread() +" "+ LocalDateTime.now() +"释放了连接");
        endLatch.await(100, TimeUnit.MILLISECONDS);
        Assert.assertEquals(0, errorCount.get());

    }
}
