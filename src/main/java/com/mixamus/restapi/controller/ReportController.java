package com.mixamus.restapi.controller;

import com.mixamus.restapi.entity.Order;
import com.mixamus.restapi.generated.api.ReportsApi;
import com.mixamus.restapi.generated.model.OrderStatus;
import com.mixamus.restapi.generated.model.ProductSalesDto;
import com.mixamus.restapi.repository.SalesReportFilter;
import com.mixamus.restapi.service.ReportService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReportController implements ReportsApi {

    private final ReportService reportService;

    @Override
    public ResponseEntity<List<ProductSalesDto>> getProductSalesReport(OffsetDateTime from, OffsetDateTime to,
                                                                      List<OrderStatus> status,
                                                                      BigDecimal minRevenue, Integer limit) {
        Set<Order.Status> statuses = status == null || status.isEmpty()
                ? EnumSet.copyOf(ReportService.DEFAULT_STATUSES)
                : status.stream()
                        .map(value -> Order.Status.valueOf(value.name()))
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Order.Status.class)));
        SalesReportFilter filter = new SalesReportFilter(
                from == null ? null : from.toInstant(),
                to == null ? null : to.toInstant(),
                statuses,
                minRevenue,
                limit);
        return ResponseEntity.ok(reportService.productSales(filter));
    }
}
