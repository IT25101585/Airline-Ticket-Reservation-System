document.addEventListener('DOMContentLoaded', function () {
    function todayIso() {
        var d = new Date();
        return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    }

    // Form validation shared by the home, customer search and staff lookup forms. The server repeats
    // every check, so this only saves a round trip.
    function attachValidation(form) {
        var originSelect = form.querySelector('select[name="origin"]');
        var destinationSelect = form.querySelector('select[name="destination"]');
        var dateInput = form.querySelector('input[name="date"]');
        var futureOnly = form.hasAttribute('data-future-only');

        if (dateInput && futureOnly) dateInput.min = todayIso();

        var box = document.createElement('div');
        box.className = 'alert alert-danger mt-3 mb-0 d-none';
        box.setAttribute('role', 'alert');
        form.appendChild(box);

        function show(message) {
            box.textContent = message;
            box.classList.remove('d-none');
        }

        form.addEventListener('submit', function (event) {
            box.classList.add('d-none');
            var origin = originSelect ? originSelect.value : '';
            var destination = destinationSelect ? destinationSelect.value : '';
            if (origin && destination && origin.toLowerCase() === destination.toLowerCase()) {
                event.preventDefault();
                show('Origin and destination must be different.');
                return;
            }
            if (dateInput && dateInput.value) {
                if (!/^\d{4}-\d{2}-\d{2}$/.test(dateInput.value) || isNaN(new Date(dateInput.value).getTime())) {
                    event.preventDefault();
                    show('Enter a valid departure date.');
                    return;
                }
                if (futureOnly && dateInput.value < todayIso()) {
                    event.preventDefault();
                    show("Departure date can't be in the past. Choose today or a later date.");
                }
            }
        });
    }

    document.querySelectorAll('form.searchbox').forEach(attachValidation);

    document.querySelectorAll('form.searchbox').forEach(function (form) {
        var originSelect = form.querySelector('select[name="origin"]');
        var destinationSelect = form.querySelector('select[name="destination"]');
        if (!originSelect || !destinationSelect || typeof originDestinations === 'undefined') return;

        var initialDestination = destinationSelect.dataset.selected || '';

        function allDestinations() {
            var set = new Set();
            Object.keys(originDestinations).forEach(function (origin) {
                originDestinations[origin].forEach(function (d) { set.add(d); });
            });
            return Array.from(set).sort();
        }

        function populateDestinations(origin, preselect) {
            var destinations = origin && originDestinations[origin]
                ? originDestinations[origin].slice().sort()
                : allDestinations();

            destinationSelect.innerHTML = '';

            var anyOption = document.createElement('option');
            anyOption.value = '';
            anyOption.textContent = 'Any destination';
            destinationSelect.appendChild(anyOption);

            destinations.forEach(function (d) {
                var option = document.createElement('option');
                option.value = d;
                option.textContent = d;
                destinationSelect.appendChild(option);
            });

            if (preselect && destinations.indexOf(preselect) !== -1) {
                destinationSelect.value = preselect;
            } else {
                destinationSelect.value = '';
            }
        }

        originSelect.addEventListener('change', function () {
            populateDestinations(originSelect.value, null);
        });

        populateDestinations(originSelect.value, initialDestination);
    });
});
