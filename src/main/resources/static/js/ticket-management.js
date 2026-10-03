(function () {
    'use strict';

    var rows = Array.prototype.slice.call(document.querySelectorAll('.ticket-row'));
    var search = document.getElementById('ticketSearch');
    var count = document.getElementById('visibleTicketCount');
    var currentFilter = 'ALL';

    function normalize(value) { return (value || '').toString().trim().toLowerCase(); }

    function applyFilters() {
        var q = normalize(search && search.value);
        var visible = 0;
        rows.forEach(function (row) {
            var statusOk = currentFilter === 'ALL' || row.dataset.status === currentFilter;
            var searchOk = !q || normalize(row.dataset.search).indexOf(q) !== -1;
            var show = statusOk && searchOk;
            row.style.display = show ? '' : 'none';
            if (show) visible++;
        });
        if (count) count.textContent = visible;
    }

    if (search) search.addEventListener('input', applyFilters);

    document.querySelectorAll('[data-ticket-filter]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            currentFilter = btn.dataset.ticketFilter;
            document.querySelectorAll('[data-ticket-filter]').forEach(function (b) {
                b.classList.remove('active', 'btn-primary');
                if (b.dataset.ticketFilter === 'ISSUED') b.className = 'btn btn-sm btn-outline-success';
                else if (b.dataset.ticketFilter === 'REISSUED') b.className = 'btn btn-sm btn-outline-warning';
                else if (b.dataset.ticketFilter === 'VOID') b.className = 'btn btn-sm btn-outline-danger';
                else b.className = 'btn btn-sm btn-outline-primary';
            });
            btn.className = 'btn btn-sm btn-primary active';
            applyFilters();
        });
    });

    document.querySelectorAll('.view-ticket-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var put = function (id, value) { var el = document.getElementById(id); if (el) el.textContent = value || '—'; };
            put('detailTicketNumber', btn.dataset.ticketNumber);
            put('detailPassenger', btn.dataset.passenger);
            put('detailPnr', btn.dataset.pnr);
            put('detailFlight', btn.dataset.flight);
            put('detailRoute', btn.dataset.route);
            put('detailSeat', btn.dataset.seat);
            put('detailStatus', btn.dataset.status);
            put('detailIssued', btn.dataset.issued);
            put('detailQr', btn.dataset.qrValue);
            put('detailReissuedFrom', btn.dataset.reissuedFrom);
        });
    });

    var validationInput = document.getElementById('validationInput');
    var validateBtn = document.getElementById('validateTicketBtn');
    var result = document.getElementById('validationResult');

    function setValidation(kind, icon, title, text) {
        if (!result) return;
        result.className = 'validation-result ' + kind;
        result.innerHTML = '<span class="result-icon"><i class="bi ' + icon + '"></i></span><div><strong>' + title + '</strong><small>' + text + '</small></div>';
    }

    function validateTicket() {
        var q = normalize(validationInput && validationInput.value);
        if (!q) {
            setValidation('invalid', 'bi-exclamation-circle', 'Enter a value', 'Use the ticket number or complete QR validation value.');
            return;
        }
        var match = rows.find(function (row) {
            return normalize(row.dataset.ticket) === q || normalize(row.dataset.qr) === q;
        });
        if (!match) {
            setValidation('invalid', 'bi-x-circle', 'Ticket not found', 'No current ticket record matches that ticket number or QR value.');
            return;
        }
        var status = match.dataset.status;
        var ticketNo = match.dataset.ticket;
        if (status === 'ISSUED') {
            setValidation('valid', 'bi-shield-check', 'Valid issued ticket', ticketNo + ' is currently live and valid.');
        } else if (status === 'REISSUED') {
            setValidation('history', 'bi-arrow-repeat', 'Superseded ticket', ticketNo + ' was reissued and is retained only as history.');
        } else {
            setValidation('invalid', 'bi-shield-x', 'Voided ticket', ticketNo + ' is invalid because its status is VOID.');
        }
        match.scrollIntoView({behavior: 'smooth', block: 'center'});
        match.animate([{backgroundColor:'rgba(45,140,255,.22)'},{backgroundColor:'transparent'}], {duration:1200});
    }

    if (validateBtn) validateBtn.addEventListener('click', validateTicket);
    if (validationInput) validationInput.addEventListener('keydown', function (e) { if (e.key === 'Enter') validateTicket(); });
})();
