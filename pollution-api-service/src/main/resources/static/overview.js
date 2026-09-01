/* The overview page: a card per source, refreshed on its own. Each card
   links to the source's history page. */

import {
  $, REFRESH_INTERVAL_MS, api, clear, hideError, loadPollutants, showError, sourceCard,
  updateRelativeTimes,
} from './common.js';

let overviewRequest = 0; // grows per request; a response of an older request is never rendered
let lastOverviewJson = null; // the overview last rendered, to skip rebuilding unchanged cards

async function loadOverview() {
  const requestId = ++overviewRequest;
  try {
    const sources = await api('/api/sources');
    if (requestId !== overviewRequest) return; // a newer refresh is underway; let it render
    const json = JSON.stringify(sources);
    if (json !== lastOverviewJson) {
      lastOverviewJson = json;
      renderOverview(sources);
    } else {
      updateRelativeTimes(); // nothing changed; only the "x min ago" texts move
    }
    hideError();
  } catch (e) {
    if (requestId !== overviewRequest) return;
    showError(`Could not load the sensors: ${e.message}`);
  }
}

function renderOverview(sources) {
  const grid = $('sources');
  clear(grid);
  for (const source of sources) grid.append(sourceCard(source));
  const reporting = sources.filter(s => s.currentReadings.length > 0).length;
  $('source-count').textContent = sources.length > 0
      ? `${sources.length} ${sources.length === 1 ? 'sensor' : 'sensors'} · ${reporting} reporting`
      : '';
  $('overview-empty').hidden = sources.length > 0;
}

(async function start() {
  try {
    await loadPollutants();
  } catch (e) {
    showError(`Could not load the pollutants: ${e.message}`);
  }
  await loadOverview();
  setInterval(loadOverview, REFRESH_INTERVAL_MS);
})();
