package ch.deletescape.lawnchair.bugreport;

import ch.deletescape.lawnchair.bugreport.BugReport;

interface IBugReportService {

    void sendReport(in BugReport report);

    void setAutoUploadEnabled(boolean enable);
}
