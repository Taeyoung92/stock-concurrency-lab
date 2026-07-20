package io.github.tylee.stockconcurrencylab.stock.service;

import io.github.tylee.stockconcurrencylab.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PessimisticLockStockService {

    private final StockRepository stockRepository;

    // findByIdWithPessimisticLock이 SELECT ... FOR UPDATE로 행을 잠그므로
    // 트랜잭션이 끝날 때까지 다른 스레드는 같은 행을 읽지 못하고 대기한다.
    // synchronized 버전과 달리 락과 커밋이 같은 트랜잭션 경계 안에서 함께 풀리므로
    // @Transactional을 그대로 사용해도 lost update가 발생하지 않는다.
    @Transactional
    public void decrease(Long id, Long quantity) {
        var stock = stockRepository.findByIdWithPessimisticLock(id).orElseThrow();
        stock.decrease(quantity);
    }
}
