package io.github.tylee.stockconcurrencylab.stock.controller;

import io.github.tylee.stockconcurrencylab.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping("/decrease")
    public void decrease(@RequestParam Long id, @RequestParam Long quantity) {
        stockService.decrease(id, quantity);
    }
}
