package org.textsentry.app;

/**
 * Public, redacted classification regression examples. No sender numbers,
 * account identifiers, real children's names, or private medical details.
 *
 * The two positive bodies are copied from reported false negatives.
 * Negative cases are synthetic examples of legitimate automated services.
 */
final class ClassificationFixtures {
    static final String POLITICAL_PERSUASION =
            "Republicans like John James passed a bill that drives up the demand for energy "
            + "and cuts back on energy production, meaning you'll pay more on your monthly "
            + "bills. Is your monthly energy bill increasing?\n"
            + "-Building Together\nStop to End";

    static final String GET_OUT_THE_VOTE =
            "Haven't voted yet? Find your early voting or Election Day location: "
            + "https://www.michigan.gov/sos/elections/voting/voters. "
            + "Please ask friends to vote! STOPZEND";

    static final class Case {
        final String name;
        final String sms;
        Case(String name, String sms) {
            this.name = name;
            this.sms = sms;
        }
    }

    static final Case[] SERVICE_NOTIFICATIONS = {
        new Case("pharmacy prescription ready",
                "Your prescription is ready for pickup at Example Pharmacy. Copay: $12.50."),
        new Case("pharmacy refill reminder",
                "Example Pharmacy: Your prescription refill is due this week. Use your account to request it."),
        new Case("pharmacy opt-out footer",
                "Example Pharmacy: Your prescription is ready. Reply STOP to stop pickup alerts."),
        new Case("daycare early closure",
                "Daycare: We are closing at 3 PM today due to a water main break. Please arrange pickup."),
        new Case("daycare child daily report",
                "Daycare: Your child's daily activity summary is available in the parent portal."),
        new Case("daycare tuition receipt",
                "Daycare: Your November tuition payment was processed. View your receipt in the portal."),
        new Case("daycare picture day",
                "Daycare reminder: Picture day is Thursday. Please bring a labeled change of clothes."),
        new Case("school weather closure",
                "School district: All schools are closed today due to icy roads."),
        new Case("school bus delay",
                "School transportation: Bus route 4 is running 20 minutes late this morning."),
        new Case("school lunch account",
                "School meals: Your student's lunch account balance is $8.50. Check the parent portal."),
        new Case("school meeting",
                "School reminder: The parent-teacher conference starts Wednesday at 6 PM."),
        new Case("doctor appointment",
                "Clinic: Your appointment is Thursday at 9:30 AM. Reply C to confirm or R to reschedule."),
        new Case("dentist appointment",
                "Dentist reminder: Your cleaning appointment is tomorrow at 1 PM. Please arrive early."),
        new Case("lab results available",
                "Medical lab: Your test results are ready. Sign in to the patient portal to view them."),
        new Case("hospital discharge portal",
                "Hospital: Your visit summary is now available in the patient portal."),
        new Case("bank deposit",
                "Example Bank: Your direct deposit of $1,240 has posted to your account."),
        new Case("bank fraud review",
                "Example Bank alert: Please review a recent card transaction in your secure banking app."),
        new Case("credit card payment",
                "Credit card alert: Your payment of $250 was received. Thank you."),
        new Case("delivery out for delivery",
                "Shipping update: Your order is out for delivery and should arrive today."),
        new Case("delivery completed",
                "Courier update: Your package was delivered to your front door at 2:15 PM."),
        new Case("grocery order",
                "Grocery delivery: Your order has been placed and will arrive between 4 and 5 PM."),
        new Case("electric outage",
                "Electric utility alert: Power is out in your area. Crews are working to restore service."),
        new Case("water maintenance",
                "Water department: Your water service will be interrupted from 8 AM to noon for maintenance."),
        new Case("flight gate change",
                "Airline notice: Flight 123 has moved to gate C17. Boarding begins at 5:20 PM."),
        new Case("train delay",
                "Transit alert: The 8:15 AM train is delayed 12 minutes due to a signal issue."),
        new Case("library hold",
                "Public library: Your reserved book is ready for pickup. Please collect it by Friday."),
        new Case("city trash schedule",
                "City service alert: Trash pickup is one day late this week due to the holiday."),
        new Case("restaurant reservation",
                "Restaurant: Your reservation for two is confirmed for 7 PM. Reply CANCEL to cancel."),
        new Case("rideshare arrival",
                "Rideshare update: Your driver is arriving in three minutes at the pickup point."),
        new Case("employee schedule",
                "Work schedule: Your next shift starts tomorrow at 7 AM. Check the staff portal."),
        new Case("insurance claim",
                "Health insurer: Your claim has been processed. View the explanation of benefits online."),
        new Case("official voter registration",
                "County clerk: We processed your voter registration address change. Review your status online."),
        new Case("official polling-place change",
                "County elections office: Your precinct's polling place has moved to the community center."),
        new Case("nonpolitical community meeting",
                "Community center: The neighborhood safety meeting is Wednesday at 7 PM in room 2.")
    };

    private ClassificationFixtures() {}
}
