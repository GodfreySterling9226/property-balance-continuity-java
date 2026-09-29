package example.property;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PropertyContinuityService {
    public record MaintenanceRequest(String property, String summary, boolean safetyCritical) {}
    public record TenantDocument(String property, String tenant, String document, LocalDate expiresOn) {}
    public record InspectionReminder(String property, String inspection, LocalDate dueOn) {}
    public record PortfolioState(List<MaintenanceRequest> maintenance,
                                 List<TenantDocument> documents,
                                 List<InspectionReminder> inspections) {}
    public record RechargeNotice(String subject, String text, int actionCount) {}

    public RechargeNotice noticeForRecharge(PortfolioState state, LocalDate today, int rechargeAmount) {
        List<String> actions = new ArrayList<>();
        state.maintenance().stream()
                .filter(MaintenanceRequest::safetyCritical)
                .sorted(Comparator.comparing(MaintenanceRequest::property))
                .forEach(item -> actions.add("MAINTENANCE | " + item.property() + " | " + item.summary()));
        state.documents().stream()
                .filter(item -> !item.expiresOn().isAfter(today.plusDays(30)))
                .sorted(Comparator.comparing(TenantDocument::expiresOn))
                .forEach(item -> actions.add("DOCUMENT | " + item.property() + " | " + item.tenant()
                        + " | " + item.document() + " expires " + item.expiresOn()));
        state.inspections().stream()
                .filter(item -> !item.dueOn().isAfter(today.plusDays(14)))
                .sorted(Comparator.comparing(InspectionReminder::dueOn))
                .forEach(item -> actions.add("INSPECTION | " + item.property() + " | " + item.inspection()
                        + " due " + item.dueOn()));

        String header = "Recharge applied: " + rechargeAmount + ". Open property actions: " + actions.size();
        String text = actions.isEmpty() ? header + "\nNo time-sensitive actions." : header + "\n" + String.join("\n", actions);
        return new RechargeNotice("Property service balance restored", text, actions.size());
    }
}
