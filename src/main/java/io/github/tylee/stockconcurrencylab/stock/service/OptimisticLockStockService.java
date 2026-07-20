package io.github.tylee.stockconcurrencylab.stock.service;

import io.github.tylee.stockconcurrencylab.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OptimisticLockStockService {

    private final StockRepository stockRepository;

    // findByIdWithOptimisticLock은 조회 시점의 @Version 값을 함께 읽어 두고,
    // 트랜잭션 커밋(flush) 시 DB의 version과 비교한다.
    // 그 사이 다른 트랜잭션이 먼저 커밋해 version이 바뀌었다면
    // ObjectOptimisticLockingFailureException이 발생한다.
    // 락을 걸어 대기시키지 않고 곧바로 실패하므로, 충돌 시 재시도는 호출자(Facade) 몫이다.
    @Transactional
    public void decrease(Long id, Long quantity) {
        var stock = stockRepository.findByIdWithOptimisticLock(id).orElseThrow();
        stock.decrease(quantity);
    }
}
