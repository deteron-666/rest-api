package com.mixamus.restapi.service;

import com.mixamus.restapi.entity.Order;
import com.mixamus.restapi.generated.model.ProductSalesDto;
import com.mixamus.restapi.repository.ProductSalesRow;
import com.mixamus.restapi.repository.SalesReportFilter;
import com.mixamus.restapi.repository.SalesReportRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        dto.setAverageUnitPrice(row.averageUnitPrice());
        dto.setRevenueSharePercent(row.revenue()
                .multiply(HUNDRED)
                .divide(row.totalRevenue(), 2, RoundingMode.HALF_UP));
        dto.setCurrentStock(row.currentStock());
        return dto;
    }
}
