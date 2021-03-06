/**
 * Copyright (C) 2018 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.gtfs_transformer.impl;

import org.onebusaway.gtfs.model.*;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.services.GtfsTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

public class CountAndTestBus implements GtfsTransformStrategy {

    private final Logger _log = LoggerFactory.getLogger(CountAndTestBus.class);

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public void run(TransformContext context, GtfsMutableRelationalDao dao) {
        GtfsMutableRelationalDao reference = (GtfsMutableRelationalDao) context.getReferenceReader().getEntityStore();
        String agency = dao.getAllTrips().iterator().next().getId().getAgencyId();

        HashMap<String, Route> referenceRoutes = new HashMap<>();
        for (Route route : reference.getAllRoutes()) {
            referenceRoutes.put(route.getId().getId(), route);
        }

        HashMap<String, Trip> referenceTrips = new HashMap<>();
        for (Trip trip : reference.getAllTrips()) {
            referenceTrips.put(trip.getId().getId(), trip);
        }

        HashMap<String, Stop> referenceStops = new HashMap<>();
        for (Stop stop : reference.getAllStops()) {
            referenceStops.put(stop.getId().getId(), stop);
        }

        int matches = 0;
        for(Route route : dao.getAllRoutes()) {
            if (referenceRoutes.containsKey(route.getId().getId())){
                matches++;
            }
            else {
                _log.info("ATIS route {} doesn't have match in reference", route.getId().getId());
            }
        }
        _log.info("ATIS Routes: {}, References: {}, ATIS match to reference: {}", dao.getAllRoutes().size(), reference.getAllRoutes().size(), matches);

        int countSt = 0;
        int countCd = 0;

        int countNoSt = 0;
        int countNoCd = 0;
        int curSerTrips = 0;

        AgencyAndId serviceAgencyAndId = new AgencyAndId();
        matches = 0;
        for (Trip trip : dao.getAllTrips()) {
            if (referenceTrips.containsKey(trip.getId().getId())) {
                matches++;
            }

            if (dao.getStopTimesForTrip(trip).size() == 0) {
                countNoSt++;
            }
            countSt = countSt + dao.getStopTimesForTrip(trip).size();
            serviceAgencyAndId = trip.getServiceId();
            if (dao.getCalendarDatesForServiceId(serviceAgencyAndId).size() == 0) {
                countNoCd++;
            }
            countCd = countCd + dao.getCalendarDatesForServiceId(serviceAgencyAndId).size();

            //check for current service
            for (ServiceCalendarDate calDate : dao.getCalendarDatesForServiceId(trip.getServiceId())) {
                Date date = calDate.getDate().getAsDate();
                Date today = removeTime(new Date());
                if (date.equals(today)) {
                    curSerTrips++;
                    break;
                }
            }
        }

        _log.info("ATIS Trips: {}, Reference: {}, match: {}, Current Service: {}", dao.getAllTrips().size(), reference.getAllTrips().size(), matches, curSerTrips);
        _log.info("Total stop times {}, Stop times for Trips: {}, Trips w/out st: {}", dao.getAllStopTimes().size(), countSt, countNoSt);
        _log.info("Total calendar dates {}, Calendar dates for Trips {}, Trips w/out cd: {}", dao.getAllCalendarDates().size(), countCd, countNoCd);

        matches = 0;
        for (Stop stop : dao.getAllStops()) {
            if(referenceStops.containsKey(stop.getId().getId())){
                matches++;
            }
        }
        _log.info("ATIS Stops: {}, Reference: {}, ATIS match to reference: {}", dao.getAllStops().size(), reference.getAllStops().size(), matches);

        if (curSerTrips < 1) {
            throw new IllegalStateException(
                    "There is no current service!!");
        }
    }

    private Date removeTime(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        date = calendar.getTime();
        return date;
    }
}