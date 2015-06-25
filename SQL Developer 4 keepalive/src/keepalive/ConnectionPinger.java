package keepalive;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.Date;

import oracle.dbtools.raptor.utils.Connections;

import oracle.ide.log.LogManager;

public class ConnectionPinger implements Runnable {

    private boolean execute;
    private int interval;

    public ConnectionPinger(Integer interval) {
        try {
            this.interval = interval;
            if (this.interval < 1) {
                LogMessage("WARNING", "Timeout is too low (less than 60 seconds).");
                LogMessage("INFO", "Timeout set to default (600 seconds).");
                this.interval = 600;
            } else {
                LogMessage("INFO", "Timeout set to " + this.interval + " seconds.");
            }
        } catch (Exception e) {
            LogMessage("WARNING", "Error while setting timeout.");
            LogMessage("INFO", "Timeout set to default (600 seconds).");
            this.interval = 600;
        }
    }

    private static final void LogMessage(String level, String msg) {
        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        Date date = new Date();
        String currentTime = "[" + dateFormat.format(date) + "] ";
        LogManager.getLogManager().getMsgPage().log("KEEPALIVE " + currentTime + level + ": " + msg + "\n");
    }

    @Override
    public void run() {
        this.execute = true;
        LogMessage("INFO", "keepalive started.");
        while (this.execute) {
            Statement sqlStatement = null;
            try {
                String[] l = Connections.getInstance().getConnNames();
                LogMessage("INFO", "keepalive event triggered, scanning " + l.length + " connections...");
                for (int i = 0; i < l.length; i++) {
                    String connectionName = l[i];
                    String displayConnectionName = connectionName;
                    if (displayConnectionName.contains("%23")) {
                        displayConnectionName = connectionName.substring(connectionName.indexOf("%23") + 3);
                    }
                    if (Connections.getInstance().isConnectionOpen(connectionName)) {
                        sqlStatement = Connections.getInstance().getConnection(connectionName).createStatement();
                        String QueryString = "SELECT SYSDATE FROM DUAL";
                        ResultSet rs = sqlStatement.executeQuery(QueryString);
                        while (rs.next()) {
                            LogMessage("DEBUG", "The keepalive query returned: " + rs.getString("SYSDATE"));
                        }
                        LogMessage("INFO", displayConnectionName + " successfully kept alive!");
                    } else {
                        // NO ACTION (the connection is not open)
                    }
                }
                LogMessage("INFO", "keepalive event finished, next event in " + this.interval + " seconds.");
                Thread.sleep(this.interval * 1000);
            } catch (InterruptedException e) {
                this.execute = false;
                LogMessage("INFO", "keepalive stopped.");
            } catch (Exception e) {
                LogMessage("ERROR", e.getMessage());
                // for other errors we can continue pinging connections
                // but sleep the thread to avoid infinite loop pinging the connection thousands times a second
                try {
                    Thread.sleep(this.interval * 1000);
                } catch (InterruptedException f) {
                    this.execute = false;
                    LogMessage("INFO", "keepalive stopped.");
                }
            } finally {
                if (sqlStatement != null) {
                    try {
                        sqlStatement.close();
                    } catch (SQLException e) {
                        LogMessage("ERROR", e.getMessage());
                    }
                }
            }
        }
    }
}
