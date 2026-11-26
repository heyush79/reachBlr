/** How the times are worked out, and where the data comes from (wording from the prototype, corrected). */
export function Footer() {
  return (
    <footer>
      <h2>How the times are worked out</h2>
      <p>
        <b>Metro.</b> Ride times and the gap between trains come from the Namma Metro weekday timetable (BMRCL GTFS,
        version of 17 August 2026, from the unofficial <a href="https://github.com/Vonter/bmrcl-gtfs">bmrcl-gtfs</a>{" "}
        project). Waiting time is half the gap between trains at that station. Getting from the street to the platform
        adds 2 minutes, leaving adds 1 minute, and changing lines adds 3 minutes plus the wait. Only lines open in the
        timetable (Purple, Green, Yellow) are included.
      </p>
      <p>
        <b>Bus.</b> Routes, stops and ride times come from the BMTC weekday timetable (version of 9 November 2026, from
        the unofficial <a href="https://github.com/Vonter/bmtc-gtfs">bmtc-gtfs</a> project, built from the Namma BMTC app;
        it only includes routes with live tracking). The timetable assumes about 22 km/h at every hour, but news reports
        put real bus speeds at 10 to 12 km/h in peak traffic, so bus rides here take 1.8 times the timetable time in the
        morning and 1.4 times in the evening. When several routes go from your stop to the same next stop, their buses
        are counted together for the wait. The wait is half the gap between buses, at least 1 minute and at most 15
        minutes, since riders can check live bus times in the app. Morning counts buses from 7 to 11 am, evening from
        7:30 to 10 pm. Changing between buses, or between bus and metro, means walking up to 250 m between bus stops or
        400 m to a metro station.
      </p>
      <p>
        <b>Getting there.</b> Walking is 4.8 km/h along a path 30% longer than a straight line. An auto or cab is 18 km/h
        in traffic along a road 40% longer than a straight line, plus 5 minutes to get one. A ward counts as reachable
        if you can get to its nearest part, plus a 300 m walk into it, so the far side of a large ward will take longer.
        A trip must include at least one bus or metro ride, unless the ward's centre is close enough to walk or ride to
        directly. Times are from your office outward; the morning trip to work is usually close to this. Office
        locations are approximate.
      </p>
      <p>
        Ward boundaries: 243 BBMP wards from{" "}
        <a href="https://github.com/datameet/Municipal_Spatial_Data">DataMeet Municipal Spatial Data</a> (CC BY 4.0).
        Timetables: Vonter's unofficial GTFS feeds (ODbL). Basemap: <a href="https://openfreemap.org">OpenFreeMap</a> ©{" "}
        <a href="https://www.openmaptiles.org/">OpenMapTiles</a>, data ©{" "}
        <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>.
      </p>
    </footer>
  );
}
