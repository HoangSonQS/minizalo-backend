package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendOtpRequest {

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(03|05|07|08|09)[0-9]{8}$", message = "Số điện thoại phải là 10 chữ số, bắt đầu bằng 03, 05, 07, 08 hoặc 09")
    private String phone;
}
