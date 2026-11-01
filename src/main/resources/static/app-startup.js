// Components and navigation are wired before this asynchronous loading sequence starts.

export async function startApplication({ loadCovers, loadProfile, loadBooks, openInitialRoute, notify }) {
  try {
    await loadCovers();
  } catch (_) {
  }
  await loadProfile(). catch (error => notify(error.message));
  await loadBooks();
  openInitialRoute();
}
