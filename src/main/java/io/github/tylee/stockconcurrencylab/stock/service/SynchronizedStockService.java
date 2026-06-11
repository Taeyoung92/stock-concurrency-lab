package io.github.tylee.stockconcurrencylab.stock.service;

import io.github.tylee.stockconcurrencylab.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SynchronizedStockService {

    private final StockRepository stockRepository;

    // @Transactional을 사용하지 않는 이유:
    // Spring은 @Transactional을 프록시로 구현한다. 실제 실행 순서는
    //   1) 프록시가 트랜잭션 시작
    //   2) synchronized 메서드 진입 (락 획득)
    //   3) 재고 조회·차감 실행
    //   4) synchronized 메서드 종료 (락 반납)
    //   5) 프록시가 트랜잭션 커밋
    // 4번에서 락이 풀린 뒤 5번 커밋 전에 다른 스레드가 메서드에 진입해
    // 아직 반영되지 않은 값을 읽는다 → 결국 락이 있어도 lost update 발생
    // @Transactional을 제거하면 각 Repository 호출이 즉시 커밋되므로
    // 락 안에서 읽고 쓴 값이 반드시 DB에 반영된 뒤 다음 스레드가 진입한다
    public synchronized void decrease(Long id, Long quantity) {
        var stock = stockRepository.findById(id).orElseThrow();
        stock.decrease(quantity);
        // saveAndFlush로 즉시 DB에 반영해 다음 스레드가 최신 값을 읽도록 보장
        stockRepository.saveAndFlush(stock);
    }
}
