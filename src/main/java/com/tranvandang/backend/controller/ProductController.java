package com.tranvandang.backend.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranvandang.backend.dto.request.*;
import com.tranvandang.backend.dto.response.ProductResponse;
import com.tranvandang.backend.dto.response.UserResponse;
import com.tranvandang.backend.entity.Product;
import com.tranvandang.backend.exception.AppException;
import com.tranvandang.backend.exception.ErrorCode;
import com.tranvandang.backend.service.ProductService;
import com.tranvandang.backend.util.ProductStatus;
import com.tranvandang.backend.util.UserStatus;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductController {
    ProductService productService;

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    // API nhận request POST với dữ liệu dạng multipart/form-data
    public ApiResponse<ProductResponse> createProduct(
            @RequestPart("product") String productJson,  // Nhận JSON dưới dạng String từ request
            @RequestPart(value = "images", required = false) MultipartFile[] images) {  // Nhận danh sách ảnh (nếu có)
        ObjectMapper objectMapper = new ObjectMapper();  // Khởi tạo ObjectMapper để chuyển đổi JSON thành object Java
        ProductRequest productRequest;
        try {
            productRequest = objectMapper.readValue(productJson, ProductRequest.class);  // Chuyển JSON thành object ProductRequest
        } catch (Exception e) {
            throw new AppException(ErrorCode.INVALID_JSON);  // Ném lỗi khi parse JSON thất bại
        }
        return ApiResponse.<ProductResponse>builder()  // Trả về API response dạng generic
                .result(productService.create(productRequest, images))  // Gọi service để tạo sản phẩm và nhận kết quả
                .build();
    }

    // Cập nhật thông tin user theo id
    @PutMapping("/{productId}")
    ProductResponse updateUser(@PathVariable String productId, @RequestBody ProductUpdateRequest request) {
        return productService.updateProduct(productId, request);
    }


    @GetMapping
    public ApiResponse<Page<ProductResponse>> getProducts(@ModelAttribute ProductFilterRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        authentication.getAuthorities().forEach(grantedAuthority -> log.info(grantedAuthority.getAuthority()));

        // Convert page from 1-based to 0-based
        request.setPage(Math.max(0, request.getPage() - 1));

        Page<ProductResponse> products = productService.getProducts(request);

        return ApiResponse.<Page<ProductResponse>>builder()
                .result(products)
                .build();
    }



    @GetMapping("/search")
    public Page<Product> searchProducts(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size) {
        return productService.searchProducts(keyword, page - 1, size);
    }

    @GetMapping("/{productId}")
    ProductResponse getProduct(@PathVariable("productId") String productId) {
        return productService.getProduct(productId);
    }

    @DeleteMapping("/{productId}")
    ApiResponse<Void>deleteProduct(@PathVariable String productId) {
        productService.deleteProduct(productId);
        return ApiResponse.<Void>builder().message("Delete successfully").build();
    }

    @GetMapping("/{id}/related")
    public ResponseEntity<List<ProductResponse>> getRelated(@PathVariable String id) {
        return ResponseEntity.ok(productService.getRelatedProducts(id));
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<Void> deleteMany(@RequestBody List<String> ids) {
        productService.deleteProducts(ids);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(productService.getProductStatistics());
    }


    @PatchMapping("/{productId}")
    public ApiResponse<ProductResponse> changerStatus(
            @PathVariable String productId,
            @RequestParam ProductStatus status) {

        log.info("Request changer user status, productId = {}", productId);

        ProductResponse response = productService.changerStatus(productId, status);

        return ApiResponse.<ProductResponse>builder()
                .result(response)
                .build();
    }
}
