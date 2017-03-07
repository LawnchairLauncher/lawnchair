package ch.deletescape.lawnchair;

public interface CustomAppWidget {
    String getLabel();
    int getPreviewImage();
    int getIcon();
    int getWidgetLayout();

    int getSpanX();
    int getSpanY();
    int getMinSpanX();
    int getMinSpanY();
    int getResizeMode();
}
