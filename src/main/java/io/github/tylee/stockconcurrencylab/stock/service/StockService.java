package io.github.tylee.stockconcurrencylab.stock.service;

import io.github.tylee.stockconcurrencylab.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    // @Transactional이 붙어 있어 메서드 전체가 하나의 DB 트랜잭션으로 묶인다.
    // 하지만 여러 스레드가 동시에 이 메서드를 호출하면 다음 문제가 생긴다:
    //   스레드 A가 재고=100을 읽은 뒤 커밋하기 전에
    //   스레드 B도 재고=100을 읽어버리면 두 스레드 모두 99를 저장한다.
    // 결과적으로 2번 차감했는데 재고는 1만 줄어드는 lost update가 발생한다.
    @Transactional
    public void decrease(Long id, Long quantity) {
        var stock = stockRepository.findById(id).orElseThrow();
        stock.decrease(quantity);
    }
}
