document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('select[name="status"]').forEach(function (select) {
        if (select.closest('.flight-status-form')) {
            skylankaToggleDelayInput(select);
        }
    });

    var routePicker = document.getElementById('route-picker');
    if (routePicker) {
        routePicker.addEventListener('change', function () {
            if (!routePicker.value) return;
            var parts = routePicker.value.split('|');
            var originField = document.getElementById('flight-origin');
            var destinationField = document.getElementById('flight-destination');
            if (originField) originField.value = parts[0];
            if (destinationField) destinationField.value = parts[1];
        });
    }

    var aircraftPicker = document.getElementById('aircraft-picker');
    if (aircraftPicker) {
        aircraftPicker.addEventListener('change', function () {
            if (!aircraftPicker.value) return;
            var parts = aircraftPicker.value.split('|');
            var aircraftField = document.getElementById('flight-aircraft');
            var capacityField = document.getElementById('flight-seatCapacity');
            if (aircraftField) aircraftField.value = parts[0];
            if (capacityField) capacityField.value = parts[1];
        });
    }

    document.querySelectorAll('form[data-loading]').forEach(function (form) {
        form.addEventListener('submit', function () {
            var btn = form.querySelector('button[type="submit"], button:not([type])');
            if (!btn || btn.disabled) return;

            btn.dataset.originalText = btn.textContent;
            btn.textContent = form.dataset.loading;
            btn.disabled = true;
            btn.classList.add('is-loading');
        });
    });

    var methodSelect = document.getElementById('method');
    if (methodSelect) {
        skylankaTogglePaymentFields(methodSelect.value);
    }

    var cardNumber = document.getElementById('mock-card-number');
    if (cardNumber) {
        cardNumber.addEventListener('input', function () {
            var digits = cardNumber.value.replace(/\D/g, '').slice(0, 16);
            cardNumber.value = digits.replace(/(.{4})/g, '$1 ').trim();
        });
    }

    var cardExpiry = document.getElementById('mock-card-expiry');
    if (cardExpiry) {
        cardExpiry.addEventListener('input', function () {
            var digits = cardExpiry.value.replace(/\D/g, '').slice(0, 4);
            cardExpiry.value = digits.length > 2 ? digits.slice(0, 2) + '/' + digits.slice(2) : digits;
        });
    }

    var cardCvv = document.getElementById('mock-card-cvv');
    if (cardCvv) {
        cardCvv.addEventListener('input', function () {
            cardCvv.value = cardCvv.value.replace(/\D/g, '').slice(0, 4);
        });
    }

    var bankAccount = document.getElementById('mock-bank-account');
    if (bankAccount) {
        bankAccount.addEventListener('input', function () {
            var digits = bankAccount.value.replace(/\D/g, '').slice(0, 14);
            bankAccount.value = digits.replace(/(.{4})/g, '$1 ').trim();
        });
    }
});

function skylankaTogglePaymentFields(method) {
    var cardFields = document.getElementById('mock-card-fields');
    var bankFields = document.getElementById('mock-bank-fields');
    if (!cardFields || !bankFields) return;

    var isBankTransfer = method === 'BANK_TRANSFER';
    cardFields.classList.toggle('hidden', isBankTransfer);
    bankFields.classList.toggle('hidden', !isBankTransfer);

    // A field hidden only via CSS still blocks form submission if it's
    // "required" — toggle that attribute alongside visibility so only the
    // fields relevant to the chosen payment method are actually validated.
    cardFields.querySelectorAll('input').forEach(function (input) {
        input.required = !isBankTransfer;
        if (isBankTransfer) input.value = '';
    });
    bankFields.querySelectorAll('input').forEach(function (input) {
        input.required = isBankTransfer;
        if (!isBankTransfer) input.value = '';
    });
}

function skylankaToggleDelayInput(select) {
    var input = select.closest('.flight-status-form').querySelector('.delay-input');
    if (!input) return;
    input.classList.toggle('hidden', select.value !== 'DELAYED');
}
