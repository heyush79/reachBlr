# Bengaluru Commute Map

Pick your office, a maximum commute time and how you get to stops. The map shades every Bengaluru ward you can reach by Namma Metro and BMTC bus within that time, and shows what each ward has nearby.

**Status:** being rebuilt from the prototype in `reference/` as a Java (Spring Boot) backend and a React frontend.

- `pipeline/`: Python scripts that build the data in `data/`. See [pipeline/README.md](pipeline/README.md).
- `data/`: prepared transit, ward and place data. It is committed so the app runs without the 100 MB download.
- `reference/`: the original single-file prototype and its notes.

Data: Namma Metro and BMTC timetables (unofficial GTFS feeds by Vonter, ODbL), BBMP wards (DataMeet, CC BY 4.0), places © OpenStreetMap contributors (ODbL).
