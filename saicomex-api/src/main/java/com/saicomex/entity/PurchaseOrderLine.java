package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SRS §19 — one line of a {@link PurchaseOrder}. No audit columns of its own;
 * it lives and dies with its order (cascade). {@code receivedQuantity} tracks
 * partial goods receipts against the ordered {@code quantity}.
 */
@Entity
@Table(name = "purchase_order_lines")
@Getter
@Setter
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "item_id")
    private Long itemId;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "received_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @Column(length = 20)
    private String unit;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal lineTotal;
}
