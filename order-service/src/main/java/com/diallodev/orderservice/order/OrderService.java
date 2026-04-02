```java
package com.diallodev.orderservice.order;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional
public class OrderService {

    private final Mapper mapper;
    private final OrderRepository orderRepository;
    private final WebClient webClient;
    private static final Logger logger = LogManager.getLogger(OrderService.class);

    public void placeOrder(OrderRequest orderRequest) {
        logger.info("Placing an order with request: {}", orderRequest);
        Order order = Order.builder()
                .orderNumber(UUID.randomUUID().toString())
                .build();

        List<OrderLineItems> orderLineItems = orderRequest.orderLineItems().stream()
                .map(mapper::toEntity).toList();

        order.setOrderLineItems(orderLineItems);

        List<String> skuCodes = order.getOrderLineItems().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();
        /* Call Inventory service, and place order if product is in stock */

        logger.debug("Fetching inventory status for SKUs: {}", skuCodes);
        InventoryResponse[] inventoryResponseArray = webClient.get().uri("http://localhost:8082/api/v1/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        logger.debug("Received inventory response: {}", inventoryResponseArray);

        var allProductsInStock = false;
        if (inventoryResponseArray != null) {
            allProductsInStock = Arrays.stream(inventoryResponseArray).allMatch(InventoryResponse::isInStock);
        }

        if (Boolean.TRUE.equals(allProductsInStock)) {
            logger.info("All products are in stock, saving order.");
            orderRepository.save(order);
        } else {
            logger.error("Not all products are in stock, throwing exception.");
            throw new IllegalArgumentException("Product is not in stock, please try again later.");
        }
    }
}
```