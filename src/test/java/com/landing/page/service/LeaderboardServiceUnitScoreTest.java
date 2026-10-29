package com.landing.page.service;

import com.landing.page.dto.response.LeaderboardItemResponse;
import com.landing.page.entity.Employee;
import com.landing.page.entity.Unit;
import com.landing.page.entity.enums.Role;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.repository.MissionRepository;
import com.landing.page.repository.SubmissionRepository;
import com.landing.page.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaderboardServiceUnitScoreTest {

    private final UnitRepository unitRepository = mock(UnitRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final MissionRepository missionRepository = mock(MissionRepository.class);
    private final EmployeeService employeeService = mock(EmployeeService.class);
    private LeaderboardService service;

    private final List<Employee> employees = new ArrayList<>();
    private final List<Object[]> validCounts = new ArrayList<>();
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        service = new LeaderboardService(unitRepository, employeeRepository, submissionRepository, missionRepository, employeeService);
        ReflectionTestUtils.setField(service, "excludedUnitCodes", Set.of("PB_BTC"));
        when(employeeService.getMinRequiredMissions()).thenReturn(1L);
        when(missionRepository.count()).thenReturn(4L);
        when(employeeRepository.findAll()).thenReturn(employees);
        when(submissionRepository.countSubmissionsGroupedByEmployee(any())).thenReturn(validCounts);
        when(submissionRepository.findMaxLastUpdatedAtGroupedByUnit()).thenReturn(List.of());
    }

    private Unit unit(long id, String code, int personnel) {
        return Unit.builder().id(id).code(code).name("Phòng " + code).totalPersonnel(personnel).build();
    }

    private void player(Unit u, long validMissions) {
        player(u, validMissions, Role.ROLE_USER, true);
    }

    private void player(Unit u, long validMissions, Role role, boolean verified) {
        Employee e = Employee.builder().id(nextId++).unit(u).role(role).isEmailVerified(verified).build();
        employees.add(e);
        if (validMissions > 0) validCounts.add(new Object[]{e.getId(), validMissions});
    }

    private Map<String, LeaderboardItemResponse> byCode(List<LeaderboardItemResponse> list) {
        Map<String, LeaderboardItemResponse> m = new java.util.HashMap<>();
        list.forEach(r -> m.put(r.getUnitCode(), r));
        return m;
    }

    @Test
    void scoreIsSubmittedMissionsOverPersonnelTimesMissions() {
        Unit b = unit(1, "B", 10);   // 10 người → 40 điểm tối đa
        Unit c = unit(2, "C", 2);    // 2 người → 8 điểm tối đa
        when(unitRepository.findAll()).thenReturn(List.of(b, c));
        player(b, 4); // user A nộp đủ 4 mission → phòng B có 4 điểm
        player(c, 4);
        player(c, 2);

        List<LeaderboardItemResponse> result = service.getUnitLeaderboard();
        Map<String, LeaderboardItemResponse> r = byCode(result);

        assertThat(r.get("B").getEarnedPoints()).isEqualTo(4);
        assertThat(r.get("B").getMaxPoints()).isEqualTo(40);
        assertThat(r.get("B").getScorePercent()).isEqualTo(10.0);
        assertThat(r.get("C").getEarnedPoints()).isEqualTo(6);
        assertThat(r.get("C").getMaxPoints()).isEqualTo(8);
        assertThat(r.get("C").getScorePercent()).isEqualTo(75.0);
        assertThat(r.get("C").getTotalMissions()).isEqualTo(4);

        // Xếp theo tỷ lệ % chứ không theo tổng điểm tuyệt đối
        assertThat(result.get(0).getUnitCode()).isEqualTo("C");
        assertThat(result.get(0).getRank()).isEqualTo(1);
        assertThat(result.get(1).getRank()).isEqualTo(2);
    }

    @Test
    void adminsUnverifiedAndExcludedUnitsDoNotCount() {
        Unit b = unit(1, "B", 5);
        Unit btc = unit(2, "PB_BTC", 3);
        when(unitRepository.findAll()).thenReturn(List.of(b, btc));
        player(b, 3);
        player(b, 4, Role.ROLE_ADMIN, true);
        player(b, 4, Role.ROLE_USER, false);
        player(btc, 4);

        List<LeaderboardItemResponse> result = service.getUnitLeaderboard();

        assertThat(result).extracting(LeaderboardItemResponse::getUnitCode).containsExactly("B");
        assertThat(result.get(0).getEarnedPoints()).isEqualTo(3);
        assertThat(result.get(0).getMaxPoints()).isEqualTo(20);
        assertThat(result.get(0).getScorePercent()).isEqualTo(15.0);
    }

    @Test
    void neverExceedsHundredPercent() {
        Unit b = unit(1, "B", 1); // khai báo 1 nhân sự nhưng có 3 người chơi nộp đủ
        when(unitRepository.findAll()).thenReturn(List.of(b));
        player(b, 4);
        player(b, 4);
        player(b, 9); // dữ liệu bất thường: không cộng quá số mission

        LeaderboardItemResponse r = service.getUnitLeaderboard().get(0);
        assertThat(r.getEarnedPoints()).isEqualTo(12);
        assertThat(r.getMaxPoints()).isEqualTo(12);
        assertThat(r.getScorePercent()).isEqualTo(100.0);
    }

    @Test
    void tiedUnitsShareRankAndRoundingIsTwoDecimals() {
        Unit a = unit(1, "A", 3);
        Unit b = unit(2, "B", 3);
        Unit z = unit(3, "Z", 3);
        when(unitRepository.findAll()).thenReturn(List.of(a, b, z));
        player(a, 1); // 1/12 = 8.33%
        player(b, 1);

        Map<String, LeaderboardItemResponse> r = byCode(service.getUnitLeaderboard());
        assertThat(r.get("A").getScorePercent()).isEqualTo(8.33);
        assertThat(r.get("A").getRank()).isEqualTo(1);
        assertThat(r.get("B").getRank()).isEqualTo(1);
        assertThat(r.get("Z").getRank()).isEqualTo(3);
        assertThat(r.get("Z").getScorePercent()).isEqualTo(0.0);
    }
}
