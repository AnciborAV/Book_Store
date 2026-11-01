import { request } from './api-client.js';
import { catalogueSnapshot } from './catalogue-model.js';

export async function loadCatalogue() {
  return catalogueSnapshot(await request('/api/catalogue'));
}
