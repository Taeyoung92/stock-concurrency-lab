package io.github.tylee.stockconcurrencylab.stock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OptimisticLockStockFacade {

    private final OptimisticLockStockService optimisticLockStockService;

    // OptimisticLockStockService.decrease는 버전 충돌 시 예외를 던지고 끝난다.
    // 재시도 로직을 @Transactional 메서드 안에 두면 같은 트랜잭션 안에서 계속 실패하므로,
    // 트랜잭션 밖인 이 파사드에서 예외를 잡아 짧게 대기한 뒤 새 트랜잭션으로 다시 시도한다.
    public void decrease(Long id, Long quantity) throws InterruptedException {
        while (true) {
            try {
                optimisticLockStockService.decrease(id, quantity);
                break;
            } catch (ObjectOptimisticLockingFailureException e) {
                Thread.sleep(50);
            }
        }
    }
}
