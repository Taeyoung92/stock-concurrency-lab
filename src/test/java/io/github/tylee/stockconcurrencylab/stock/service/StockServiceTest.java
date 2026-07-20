package io.github.tylee.stockconcurrencylab.stock.service;

import io.github.tylee.stockconcurrencylab.stock.domain.Stock;
import io.github.tylee.stockconcurrencylab.stock.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
class StockServiceTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private SynchronizedStockService synchronizedStockService;

    @Autowired
    private PessimisticLockStockService pessimisticLockStockService;

    @Autowired
    private OptimisticLockStockFacade optimisticLockStockFacade;

    @Autowired
    private StockRepository stockRepository;

    private Long stockId;

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 재고 100개짜리 데이터를 DB에 저장하고 ID를 보관한다
        Stock stock = stockRepository.save(new Stock(100L));
        stockId = stock.getId();
    }

    @AfterEach
    void tearDown() {
        // 테스트끼리 데이터가 섞이지 않도록 매 테스트 종료 후 전체 삭제
        stockRepository.deleteAll();
    }

    @Test
    void concurrentRequests_withoutLock_causesRaceCondition() throws InterruptedException {
        int threadCount = 100;

        // 스레드 풀: 최대 32개의 스레드를 미리 만들어 두고 재사용하는 실행기다.
        // 100개 작업을 한꺼번에 submit해도 동시에 실행되는 스레드는 최대 32개이며,
        // 하나가 끝나면 대기 중인 다음 작업이 빈 자리를 채운다.
        ExecutorService executorService = Executors.newFixedThreadPool(32);

        // CountDownLatch: 지정한 숫자(100)부터 0까지 카운트다운하는 신호 장치다.
        // 작업 하나가 끝날 때마다 countDown()으로 1을 빼고,
        // 아래 latch.await()를 만난 메인 스레드는 카운터가 0이 될 때까지 여기서 멈춘다.
        // 이 장치 없이 latch.await()를 생략하면 100개 작업이 끝나기 전에
        // 메인 스레드가 재고를 조회해 잘못된 결과를 검증하게 된다.
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            // submit: 작업을 스레드 풀 대기열에 넣는다. 즉시 반환되며 비동기로 실행된다.
            executorService.submit(() -> {
                try {
                    stockService.decrease(stockId, 1L);
                } finally {
                    // 예외가 발생해도 반드시 카운트를 줄여 메인 스레드가 영원히 블로킹되는 것을 방지
                    latch.countDown();
                }
            });
        }

        // 100개 작업이 전부 끝날 때까지 메인 스레드를 여기서 대기
        latch.await();
        executorService.shutdown();

        Stock result = stockRepository.findById(stockId).orElseThrow();
        System.out.println("final stock (no lock): " + result.getQuantity());

        // 락이 없으면 여러 스레드가 동시에 같은 재고 값을 읽어 중복 저장(lost update)이 발생하므로
        // 100번 차감했음에도 최종 재고가 0보다 큰 값으로 남는다
        assertNotEquals(0L, result.getQuantity());
    }

    @Test
    void concurrentRequests_synchronized_stockIsZero() throws InterruptedException {
        int threadCount = 100;

        // 동일한 구조의 스레드 풀과 래치를 사용하되, synchronized 서비스를 호출한다
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    synchronizedStockService.decrease(stockId, 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        Stock result = stockRepository.findById(stockId).orElseThrow();
        System.out.println("final stock (synchronized): " + result.getQuantity());

        // synchronized 덕분에 한 번에 하나의 스레드만 decrease를 실행하므로
        // 100번 차감 후 최종 재고는 정확히 0이어야 한다
        assertEquals(0L, result.getQuantity());
    }

    @Test
    void concurrentRequests_pessimisticLock_stockIsZero() throws InterruptedException {
        int threadCount = 100;

        // 동일한 구조의 스레드 풀과 래치를 사용하되, 비관적 락 서비스를 호출한다
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pessimisticLockStockService.decrease(stockId, 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        Stock result = stockRepository.findById(stockId).orElseThrow();
        System.out.println("final stock (pessimistic lock): " + result.getQuantity());

        // SELECT ... FOR UPDATE로 행이 잠기므로 트랜잭션이 끝날 때까지
        // 다른 스레드가 대기하고, 100번 차감 후 최종 재고는 정확히 0이어야 한다
        assertEquals(0L, result.getQuantity());
    }

    @Test
    void concurrentRequests_optimisticLock_stockIsZero() throws InterruptedException {
        int threadCount = 100;

        // 동일한 구조의 스레드 풀과 래치를 사용하되, 낙관적 락 파사드를 호출한다
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    optimisticLockStockFacade.decrease(stockId, 1L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        Stock result = stockRepository.findById(stockId).orElseThrow();
        System.out.println("final stock (optimistic lock): " + result.getQuantity());

        // 버전 충돌 시 파사드가 재시도하므로, 100번 차감 후 최종 재고는 정확히 0이어야 한다
        assertEquals(0L, result.getQuantity());
    }
}
