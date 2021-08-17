/*
 * Copyright 2019-2021 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.monitoring.core;

import java.sql.Timestamp;
import java.util.List;
import org.polypheny.db.monitoring.events.MonitoringDataPoint;
import org.polypheny.db.monitoring.events.MonitoringEvent;


/**
 * Main interface for working with the MonitoringService environment. Jobs can be registered, monitored
 * and subscriber can the registered based on MonitoringEventData
 */
public interface MonitoringService {

    /**
     * Monitor event which will be queued immediately and get processed by a registered queue worker.
     * <T> The type parameter for the event, which will implement MonitoringEventData
     *
     * @param eventData The event data object.
     */
    <T extends MonitoringEvent> void monitorEvent( T eventData );

    /**
     * Get all data for given monitoring persistent type.
     *
     * @param dataPointClass DatapointClass of interest
     * @return Returns List of all data points from teh specified dataPointClass
     */
    <T extends MonitoringDataPoint> List<T> getAllDataPoints( Class<T> dataPointClass );

    /**
     * Get data before specified timestamp for given monitoring persistent type.
     *
     * @param dataPointClass Data point class of interest to look for
     * @param timestamp Youngest timestamp t return data points from
     * @return Returns List of all data points from the specified dataPointClass
     */
    <T extends MonitoringDataPoint> List<T> getDataPointsBefore( Class<T> dataPointClass, Timestamp timestamp );

    /**
     * Get data after specified timestamp for given monitoring persistent type.
     *
     * @param dataPointClass Data point class of interest to look for
     * @param timestamp Oldest timestamp t return data points from
     * @return Returns List of all data points from teh specified dataPointClass
     */
    <T extends MonitoringDataPoint> List<T> getDataPointsAfter( Class<T> dataPointClass, Timestamp timestamp );

}