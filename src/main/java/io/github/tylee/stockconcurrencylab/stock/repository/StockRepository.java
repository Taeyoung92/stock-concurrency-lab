package io.github.tylee.stockconcurrencylab.stock.repository;

import io.github.tylee.stockconcurrencylab.stock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {
}
