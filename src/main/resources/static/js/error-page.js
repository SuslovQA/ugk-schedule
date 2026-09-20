const back = document.getElementById('errorBack');
if (window.history.length > 1) {
    back.hidden = false;
    back.addEventListener('click', () => window.history.back());
}
