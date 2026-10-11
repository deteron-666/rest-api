package com.mixamus.restapi.service;

import com.mixamus.restapi.entity.Order;
import com.mixamus.restapi.generated.model.ProductSalesDto;
import com.mixamus.restapi.repository.ProductSalesRow;
import com.mixamus.restapi.repository.SalesReportFilter;
import com.mixamus.restapi.repository.SalesReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    /**
     * Отменённые заказы — не продажи, поэтому по умолчанию в отчёт не попадают.
     */
    public static final List<Order.Status> DEFAULT_STATUSES = List.of(Order.Status.PENDING, Order.Status.PAID);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SalesReportRepository salesReportRepository;

    public List<ProductSalesDto> productSales(SalesReportFilter filter) {
        return salesReportRepository.findTopProductsByRevenue(filter).stream()
                .map(this::toDto)
                .toList();
    }

    private ProductSalesDto toDto(ProductSalesRow row) {
        ProductSalesDto dto = new ProductSalesDto();
        dto.setProductId(row.productId());
        dto.setProductName(row.productName());
        dto.setUnitsSold(row.unitsSold());
        dto.setOrdersCount(row.ordersCount());
        dto.setRevenue(row.revenue());
        dto.setAverageUnitPrice(averageUnitPrice(row));
        dto.setRevenueSharePercent(revenueSharePercent(row));
        dto.setCurrentStock(row.currentStock());
        return dto;
    }

    /**
     * Средняя цена проданной единицы — выручка / количество, то есть с учётом количества в каждой позиции.
     * avg(unitPrice) по позициям дал бы неверный результат, если цена менялась: 1 шт по 100 и 9 шт по 50
     * — это 55, а не 75. unitsSold > 0: в позиции заказа quantity > 0 (CHECK в БД).
     */
    private static BigDecimal averageUnitPrice(ProductSalesRow row) {
        return row.revenue().divide(BigDecimal.valueOf(row.unitsSold()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Общая выручка может быть нулевой: цена товара может быть 0. Тогда доля каждого товара — 0.
     */
    private static BigDecimal revenueSharePercent(ProductSalesRow row) {
        if (row.totalRevenue().signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return row.revenue()
                .multiply(HUNDRED)
                .divide(row.totalRevenue(), 2, RoundingMode.HALF_UP);
    }
}
