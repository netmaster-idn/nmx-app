package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardHomeViewDTO {

    private List<AlertItem> alerts = new ArrayList<>();
    private CustomerSummary customerSummary;
    private RevenueSummary revenueSummary;
    private DeviceSummary deviceSummary;
    private OntSummary ontSummary;
    private TicketSummary ticketSummary;
    private HealthSummary healthSummary;
    private List<String> revenueLabels = new ArrayList<>();
    private List<Long> revenueValues = new ArrayList<>();
    private List<String> healthLabels = new ArrayList<>();
    private List<Long> healthValues = new ArrayList<>();
    private List<MapPoint> mapPoints = new ArrayList<>();
    private MapSummary mapSummary;
    private List<RegistrationItem> registrations = new ArrayList<>();
    private HighlightSummary highlightSummary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertItem {
        private String label;
        private String severityClass;
        private String iconClass;
        private String title;
        private String detail;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerSummary {
        private long total;
        private long active;
        private long suspended;
        private long inactive;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueSummary {
        private String totalLabel;
        private String subtitle;
        private long invoiceCount;
        private long overdueCount;
        private String todayPaidLabel;
        private String outstandingLabel;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceSummary {
        private long total;
        private long online;
        private long offline;
        private long warning;
        private long maintenance;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OntSummary {
        private long total;
        private long online;
        private long offline;
        private long lowSignal;
        private long dyingGasp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketSummary {
        private long active;
        private long highPriority;
        private long pending;
        private long resolvedToday;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthSummary {
        private String serviceHealthRate;
        private String customerGrowthRate;
        private String inactiveRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapPoint {
        private String top;
        private String left;
        private String color;
        private String label;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapSummary {
        private long active;
        private long suspended;
        private long inactive;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegistrationItem {
        private String label;
        private long count;
        private int progressPercent;
        private boolean current;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HighlightSummary {
        private String label;
        private String value;
        private String detail;
    }
}
