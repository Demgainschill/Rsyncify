module com.rsyncify {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.rsyncify to javafx.fxml;

    exports com.rsyncify;
}
