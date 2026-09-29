package example.property;

import java.time.LocalDate;
import java.util.List;

public final class PropertyContinuityServiceTest {
    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 9, 16);
        PropertyContinuityService.PortfolioState state = new PropertyContinuityService.PortfolioState(
                List.of(
                        new PropertyContinuityService.MaintenanceRequest("A", "lift alarm", true),
                        new PropertyContinuityService.MaintenanceRequest("B", "paint hallway", false)),
                List.of(
                        new PropertyContinuityService.TenantDocument("A", "T1", "insurance", today.plusDays(30)),
                        new PropertyContinuityService.TenantDocument("B", "T2", "identity review", today.plusDays(31))),
                List.of(
                        new PropertyContinuityService.InspectionReminder("A", "fire panel", today.plusDays(14)),
                        new PropertyContinuityService.InspectionReminder("B", "roof", today.plusDays(15))));

        PropertyContinuityService.RechargeNotice notice = new PropertyContinuityService()
                .noticeForRecharge(state, today, 100);

        require(notice.actionCount() == 3, "expected three time-sensitive actions");
        require(notice.text().contains("lift alarm"), "safety maintenance must be present");
        require(notice.text().contains("insurance"), "document at 30 days must be present");
        require(notice.text().contains("fire panel"), "inspection at 14 days must be present");
        require(!notice.text().contains("paint hallway"), "routine maintenance must stay out");
        require(!notice.text().contains("identity review"), "later document must stay out");
        require(!notice.text().contains("roof"), "later inspection must stay out");
        System.out.println("PASS: recharge notice contains exactly three time-sensitive property actions");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
