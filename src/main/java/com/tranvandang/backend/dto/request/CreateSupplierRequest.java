package com.tranvandang.backend.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateSupplierRequest {
    String name;
    String phone;
    String email;
    String address;
}
