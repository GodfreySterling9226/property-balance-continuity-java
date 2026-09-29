package example.property;

import java.time.LocalDate;
import java.util.List;

public final class RechargeNotificationRunner {
    private RechargeNotificationRunner() {}

    public static void main(String[] args) {
        ServiceConfig config = ServiceConfig.fromEnvironment();
        InfraiClient infrai = new InfraiClient(config);
        int triggerBalance = integerArgument(args, "--trigger-balance", 20);
        int rechargeAmount = integerArgument(args, "--recharge-amount", 100);

        infrai.configureAutoRecharge(triggerBalance, rechargeAmount);

        PropertyContinuityService.PortfolioState state = new PropertyContinuityService.PortfolioState(
                List.of(new PropertyContinuityService.MaintenanceRequest(
                        "Maple Court", "boiler pressure alert", true)),
                List.of(new PropertyContinuityService.TenantDocument(
                        "Harbor House", "Tenant 204", "insurance certificate", LocalDate.now().plusDays(12))),
                List.of(new PropertyContinuityService.InspectionReminder(
                        "Maple Court", "fire panel", LocalDate.now().plusDays(7))));

        PropertyContinuityService.RechargeNotice notice = new PropertyContinuityService()
                .noticeForRecharge(state, LocalDate.now(), rechargeAmount);
        String messageId = infrai.sendEmail(config.operationsEmail(), notice.subject(), notice.text());
        System.out.println("Configured auto recharge and sent " + notice.actionCount()
                + " property actions. message_id=" + messageId);
    }

    private static int integerArgument(String[] args, String name, int fallback) {
        for (int index = 0; index < args.length - 1; index++) {
            if (args[index].equals(name)) {
                return Integer.parseInt(args[index + 1]);
            }
        }
        return fallback;
    }
}
