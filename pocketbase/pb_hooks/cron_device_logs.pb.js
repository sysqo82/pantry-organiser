/// <reference path="../pb_data/types.d.ts" />

/**
 * PocketBase Cron Job: deviceLogsCleanup
 * Runs every day at midnight (0 0 * * *)
 *
 * Deletes records from the 'device_logs' collection that are older than 7 days.
 */
cronAdd("deviceLogsCleanup", "0 0 * * *", (e) => {
    console.log("Running device_logs cleanup cron job...");

    try {
        // Calculate the date 7 days ago
        const date7DaysAgo = new Date();
        date7DaysAgo.setDate(date7DaysAgo.getDate() - 7);

        // Format to PocketBase datetime string format (YYYY-MM-DD HH:MM:SS.000Z)
        const dateString = date7DaysAgo.toISOString().replace('T', ' ').substring(0, 19) + ".000Z";

        // Find all log records created before that date
        const records = $app.dao().findRecordsByFilter(
            "device_logs",
            "created < {:date}",
            "-created",
            10000,
            0,
            { date: dateString }
        );

        if (records && records.length > 0) {
            console.log(`Found ${records.length} old device_logs to delete.`);

            // Delete them
            for (let record of records) {
                $app.dao().deleteRecord(record);
            }

            console.log("Cleanup complete.");
        } else {
            console.log("No old device_logs found. Cleanup skipped.");
        }
    } catch (err) {
        console.error("Error during device_logs cleanup:", err);
    }
});