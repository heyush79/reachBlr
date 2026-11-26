import type { PlaceType, WardProps } from "../types";
import { nearestText } from "../lib/text";

/** What a ward has nearby, from OpenStreetMap: score bar, count inside, nearest one. */
export function PlacesTable({ ward, types }: { ward: WardProps; types: PlaceType[] }) {
  if (!ward.count || !ward.nearest10m || !ward.scores) return null;
  return (
    <>
      <p className="sub nb-title">
        Everyday needs score <span className="score">{ward.score}</span>
      </p>
      <table className="nb">
        <caption className="visually-hidden">Places in and near {ward.name}</caption>
        <thead className="visually-hidden">
          <tr>
            <th scope="col">Type and score</th>
            <th scope="col">Inside the ward</th>
            <th scope="col">Nearest to the centre</th>
          </tr>
        </thead>
        <tbody>
          {types.map((t, k) => (
            <tr key={t.key}>
              <td>
                {t.label}
                <span className="bar" title={`Score ${ward.scores![k]} of 100`}>
                  <i style={{ width: `${ward.scores![k]}%` }} />
                </span>
              </td>
              <td>{ward.count![k]} inside</td>
              <td>{nearestText(ward.nearest10m![k])}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="sub nb-note">From OpenStreetMap, which misses many places, especially gyms and small shops.</p>
    </>
  );
}
