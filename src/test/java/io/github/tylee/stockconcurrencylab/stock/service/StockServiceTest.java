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

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
class StockServiceTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    private Long stockId;

    @BeforeEach
    void setUp() {
        Stock stock = stockRepository.save(new Stock(100L));
        stockId = stock.getId();
    }

    @AfterEach
    void tearDown() {
        stockRepository.deleteAll();
    }

    @Test
    void 동시에_100개_요청시_동시성_문제_발생() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    stockService.decrease(stockId, 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        Stock result = stockRepository.findById(stockId).orElseThrow();
        System.out.println("최종 재고: " + result.getQuantity());

        // 락이 없으면 race condition으로 인해 재고가 0이 되지 않음
        assertNotEquals(0L, result.getQuantity());
    }
}
