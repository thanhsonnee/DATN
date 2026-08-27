package com.gym.identity.api;

import com.gym.identity.api.dto.TrainerResponse;
import com.gym.identity.service.EmployeeLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Nhân sự", description = "Tra cứu nhân sự")
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeLookupService employeeLookupService;

    @Operation(summary = "Danh sách huấn luyện viên đang nhận lịch",
            description = "Hội viên chọn huấn luyện viên bằng TÊN khi đặt lịch, không cần "
                        + "biết trước mã số nhân viên.")
    @GetMapping("/trainers")
    public List<TrainerResponse> trainers() {
        return employeeLookupService.trainers();
    }
}
