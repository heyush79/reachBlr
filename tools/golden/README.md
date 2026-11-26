# Golden answers

`golden.mjs` runs the prototype's own route search (`route()` and `steps()` from
`reference/.../prototype/index.html`, cut out as text and run unchanged in a Node sandbox) on the files in `data/`.
It saves the answers to `backend/src/test/resources/golden/golden.json`.

`GoldenParityTest` in the backend then checks the Java search against these answers. For each of 12 searches it compares,
for all 243 wards:

- the minutes,
- the lines used,
- the wording of every step.

Re-run it after changing `data/`:

```bash
node tools/golden/golden.mjs
```

It needs Node 18 or newer and has no npm dependencies.
