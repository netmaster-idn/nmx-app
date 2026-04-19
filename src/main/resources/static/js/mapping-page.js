(function() {
    if (window.__mappingPageBooted) {
        return;
    }
    window.__mappingPageBooted = true;

    const DEFAULT_CENTER = [-2.5489, 118.0149];
    const DEFAULT_ZOOM = 5;
    const ALL_OPTION = 'ALL';

    const state = {
        map: null,
        icons: {},
        layers: {},
        data: {
            servers: [],
            fiberNodes: [],
            onts: [],
            links: [],
            canEdit: false
        },
        detailCache: new Map(),
        nodeIndex: new Map(),
        selectedServerId: null,
        selectedNodeKey: ALL_OPTION,
        selectedOntServiceId: ALL_OPTION,
        hoveredDetailKey: null,
        selectedLinkId: null,
        editingNodeId: null,
        editMode: false,
        persistentOntServiceId: null,
        persistentDetailKey: null,
        lastUpdatedNode: null,
        mikrotikOptions: [],
        editingServerId: null,
        drawMode: false,
        pendingLinkStart: null,
        previewLine: null,
        coordinatePickerMarker: null,
        layoutSyncFrame: null,
        sidePanelResizeObserver: null,
        dom: {}
    };

    function init() {
        if (!document.getElementById('networkMap')) {
            return;
        }

        cacheDom();
        mountModalsIntoMapCard();
        bindStaticEvents();
        initLayoutSync();
        setToolbarEditState(false);
        setStatus('Memuat library peta...', 'info');

        ensureLeaflet()
            .then(function() {
                initMap();
                return loadOverview(true);
            })
            .catch(function(error) {
                console.error('Mapping initialization failed:', error);
                showEmptyState('Library peta gagal dimuat. Periksa koneksi internet untuk mengakses tile OpenStreetMap.');
                setStatus(error.message || 'Library peta gagal dimuat.', 'error');
            });
    }

    function cacheDom() {
        state.dom.serverSelect = document.getElementById('mappingServerSelect');
        state.dom.serverSingle = document.getElementById('mappingServerSingle');
        state.dom.nodeFilter = document.getElementById('mappingNodeFilter');
        state.dom.ontFilter = document.getElementById('mappingOntFilter');
        state.dom.refreshButton = document.getElementById('mappingRefreshButton');
        state.dom.connectButton = document.getElementById('mappingConnectButton');
        state.dom.cancelButton = document.getElementById('mappingCancelButton');
        state.dom.deleteLineButton = document.getElementById('mappingDeleteLineButton');
        state.dom.toolbarNote = document.getElementById('mappingToolbarNote');
        state.dom.statusText = document.getElementById('mappingStatusText');
        state.dom.emptyState = document.getElementById('mappingMapEmpty');
        state.dom.mapCard = document.querySelector('.mapping-map-card');
        state.dom.sidePanel = document.querySelector('.mapping-side-panel');
        state.dom.detail = document.getElementById('mappingOntDetail');
        state.dom.serverCount = document.getElementById('mappingServerCount');
        state.dom.fiberCount = document.getElementById('mappingFiberCount');
        state.dom.ontCount = document.getElementById('mappingOntCount');
        state.dom.linkCount = document.getElementById('mappingLinkCount');
        state.dom.addNodeButton = document.getElementById('mappingAddNodeButton');
        state.dom.nodeModalOverlay = document.getElementById('mappingNodeModalOverlay');
        state.dom.nodeModal = document.getElementById('mappingNodeModal');
        state.dom.nodeForm = document.getElementById('mappingNodeForm');
        state.dom.nodeTypeInput = document.getElementById('mappingNodeTypeInput');
        state.dom.nodeServerInput = document.getElementById('mappingNodeServerInput');
        state.dom.nodeNameInput = document.getElementById('mappingNodeNameInput');
        state.dom.nodeSplitterInput = document.getElementById('mappingNodeSplitterInput');
        state.dom.nodeCoordinatesInput = document.getElementById('mappingNodeCoordinatesInput');
        state.dom.nodeCancelButton = document.getElementById('mappingNodeCancelButton');
        state.dom.nodeSaveButton = document.getElementById('mappingNodeSaveButton');
        state.dom.serverModalOverlay = document.getElementById('mappingServerModalOverlay');
        state.dom.serverModal = document.getElementById('mappingServerModal');
        state.dom.serverForm = document.getElementById('mappingServerForm');
        state.dom.serverNameInput = document.getElementById('mappingServerNameInput');
        state.dom.serverMikrotikInput = document.getElementById('mappingServerMikrotikInput');
        state.dom.serverLocationInput = document.getElementById('mappingServerLocationInput');
        state.dom.serverCoordinatesInput = document.getElementById('mappingServerCoordinatesInput');
        state.dom.serverLatitudeInput = document.getElementById('mappingServerLatitudeInput');
        state.dom.serverLongitudeInput = document.getElementById('mappingServerLongitudeInput');
        state.dom.serverStatusInput = document.getElementById('mappingServerStatusInput');
        state.dom.serverIpInput = document.getElementById('mappingServerIpInput');
        state.dom.serverCancelButton = document.getElementById('mappingServerCancelButton');
        state.dom.serverSaveButton = document.getElementById('mappingServerSaveButton');
    }

    function mountModalsIntoMapCard() {
        if (!state.dom.mapCard) {
            return;
        }

        [
            state.dom.nodeModalOverlay,
            state.dom.nodeModal,
            state.dom.serverModalOverlay,
            state.dom.serverModal
        ].forEach(function(element) {
            if (element && element.parentElement !== state.dom.mapCard) {
                state.dom.mapCard.appendChild(element);
            }
        });
    }

    function bindStaticEvents() {
        state.dom.refreshButton.addEventListener('click', function() {
            loadOverview(false);
        });

        state.dom.serverSelect.addEventListener('change', function(event) {
            state.selectedServerId = event.target.value || null;
            renderAll(true);
        });

        state.dom.nodeFilter.addEventListener('change', function(event) {
            state.selectedNodeKey = event.target.value || ALL_OPTION;
            state.selectedLinkId = null;
            renderAll(true);
        });

        state.dom.ontFilter.addEventListener('change', function(event) {
            state.selectedOntServiceId = event.target.value || ALL_OPTION;
            clearHoveredDetail();
            state.selectedLinkId = null;
            renderAll(true);
        });

        state.dom.connectButton.addEventListener('click', function() {
            if (!state.data.canEdit) {
                setStatus('Akun ini hanya dapat membaca peta.', 'warning');
                return;
            }

            state.drawMode = !state.drawMode;
            if (!state.drawMode) {
                clearPendingLink();
            }
            renderToolbarState();
            renderLines();
            setStatus(
                state.drawMode
                    ? 'Mode hubungkan aktif. Klik marker titik awal lalu titik tujuan.'
                    : 'Mode hubungkan dimatikan.',
                state.drawMode ? 'info' : 'success'
            );
        });

        state.dom.cancelButton.addEventListener('click', function() {
            clearPendingLink();
            state.drawMode = false;
            renderToolbarState();
            renderLines();
            setStatus('Pemilihan garis dibatalkan.', 'info');
        });

        state.dom.deleteLineButton.addEventListener('click', function() {
            if (!state.selectedLinkId) {
                setStatus('Pilih garis pada peta terlebih dahulu.', 'warning');
                return;
            }
            deleteSelectedLine();
        });

        state.dom.addNodeButton.addEventListener('click', function() {
            openNodeModal();
        });

        state.dom.nodeCancelButton.addEventListener('click', function() {
            closeNodeModal();
        });

        state.dom.nodeModalOverlay.addEventListener('click', function() {
            closeNodeModal();
        });

        state.dom.nodeForm.addEventListener('submit', function(event) {
            event.preventDefault();
            saveNodeForm();
        });

        state.dom.nodeTypeInput.addEventListener('change', function() {
            updateNodeSaveButtonLabel();
            if (!state.coordinatePickerMarker || !state.map) {
                return;
            }
            const latlng = state.coordinatePickerMarker.getLatLng();
            applyPickedCoordinates(latlng);
        });

        state.dom.serverCancelButton.addEventListener('click', function() {
            closeServerModal();
        });

        state.dom.serverModalOverlay.addEventListener('click', function() {
            closeServerModal();
        });

        state.dom.serverForm.addEventListener('submit', function(event) {
            event.preventDefault();
            saveServerForm();
        });

        state.dom.serverMikrotikInput.addEventListener('change', function() {
            updateServerMikrotikMetadata();
        });

        state.dom.serverCoordinatesInput.addEventListener('change', function() {
            applyServerCoordinatesText(state.dom.serverCoordinatesInput.value);
        });

        window.addEventListener('resize', scheduleMapCardHeightSync);

        document.addEventListener('click', function(event) {
            if (!isPersistentDetailActive()) {
                return;
            }
            if (shouldIgnoreDetailDismissal(event.target)) {
                return;
            }
            closePersistentDetail();
        });
    }

    function ensureLeaflet() {
        return new Promise(function(resolve, reject) {
            if (window.L && typeof window.L.map === 'function') {
                resolve(window.L);
                return;
            }

            const existing = document.getElementById('leaflet-script');
            if (existing) {
                existing.addEventListener('load', function() {
                    resolve(window.L);
                });
                existing.addEventListener('error', function() {
                    reject(new Error('Leaflet gagal dimuat.'));
                });
                return;
            }

            const script = document.createElement('script');
            script.id = 'leaflet-script';
            script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js';
            script.async = true;
            script.onload = function() {
                resolve(window.L);
            };
            script.onerror = function() {
                reject(new Error('Leaflet gagal dimuat.'));
            };
            document.head.appendChild(script);
        });
    }

    function initMap() {
        if (state.map || !window.L) {
            return;
        }

        state.map = L.map('networkMap', {
            zoomControl: true,
            preferCanvas: true
        }).setView(DEFAULT_CENTER, DEFAULT_ZOOM);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; OpenStreetMap contributors'
        }).addTo(state.map);

        state.layers.server = L.layerGroup().addTo(state.map);
        state.layers.fiber = L.layerGroup().addTo(state.map);
        state.layers.ont = L.layerGroup().addTo(state.map);
        state.layers.link = L.layerGroup().addTo(state.map);

        state.icons.SERVER = createIcon('server', 'fa-server');
        state.icons.ODC = createIcon('odc', 'fa-boxes-stacked');
        state.icons.ODP = createIcon('odp', 'fa-network-wired');
        state.icons.ONT = createIcon('ont', 'fa-wifi');

        state.map.on('mousemove', function(event) {
            if (!state.previewLine || !state.pendingLinkStart) {
                return;
            }
            state.previewLine.setLatLngs([state.pendingLinkStart.latlng, event.latlng]);
        });

        state.map.on('click', function(event) {
            if (isNodeModalOpen()) {
                applyPickedCoordinates(event.latlng);
                return;
            }
            if (isServerModalOpen()) {
                return;
            }
            if (isPersistentDetailActive() && !state.drawMode) {
                closePersistentDetail();
            }
            if (!state.drawMode) {
                state.selectedLinkId = null;
                renderLines();
            }
        });
    }

    function createIcon(markerClass, iconClass) {
        const innerHtml = markerClass === 'ont'
            ? '<div class="mapping-ont-logo"><i class="fas fa-router"></i><span>ONT</span></div>'
            : '<i class="fas ' + iconClass + '"></i>';
        return L.divIcon({
            className: '',
            html: '<div class="mapping-marker ' + markerClass + '">' + innerHtml + '</div>',
            iconSize: [20, 20],
            iconAnchor: [10, 10],
            popupAnchor: [0, -8]
        });
    }

    async function loadOverview(fitToBounds) {
        try {
            setStatus('Memuat data koordinat server, ODP/ODC, ONT, dan garis topologi...', 'info');
            const payload = await fetchJson('/api/mapping/overview?ts=' + Date.now());
            state.data = payload || {
                servers: [],
                fiberNodes: [],
                onts: [],
                links: [],
                canEdit: false
            };
            if (state.lastUpdatedNode) {
                state.data.fiberNodes = (state.data.fiberNodes || []).map(function(item) {
                    if (String(item.id) === String(state.lastUpdatedNode.id)
                        && String(item.type) === String(state.lastUpdatedNode.type)) {
                        return state.lastUpdatedNode;
                    }
                    return item;
                });
            }
            state.nodeIndex = buildNodeIndex();
            state.detailCache.clear();
            state.editingNodeId = null;
            state.editMode = false;
            state.persistentOntServiceId = null;
            state.persistentDetailKey = null;
            state.lastUpdatedNode = null;
            clearHoveredDetail();
            hydrateDefaultFilters();
            renderControls();
            renderSummary();
            renderToolbarState();
            scheduleMapCardHeightSync();
            renderAll(fitToBounds);
            setStatus('Peta berhasil dimuat. Hover pin ONT untuk melihat detail.', 'success');
        } catch (error) {
            console.error('Failed to load mapping overview:', error);
            showEmptyState(error.message || 'Data mapping gagal dimuat.');
            setStatus(error.message || 'Data mapping gagal dimuat.', 'error');
        }
    }

    function hydrateDefaultFilters() {
        const serverOptions = state.data.servers || [];
        if (serverOptions.length > 0) {
            const preferredServer = serverOptions.find(function(item) {
                return item.hasCoordinates;
            }) || serverOptions[0];
            if (!state.selectedServerId || !serverOptions.some(function(item) {
                return String(item.id) === String(state.selectedServerId);
            })) {
                state.selectedServerId = preferredServer ? String(preferredServer.id) : null;
            }
        } else {
            state.selectedServerId = null;
        }

        const fiberNodes = state.data.fiberNodes || [];
        if (state.selectedNodeKey !== ALL_OPTION && !fiberNodes.some(function(item) {
            return buildNodeKey(item.type, item.id) === state.selectedNodeKey;
        })) {
            state.selectedNodeKey = ALL_OPTION;
        }

        const onts = state.data.onts || [];
        if (state.selectedOntServiceId !== ALL_OPTION && !onts.some(function(item) {
            return String(item.serviceId) === String(state.selectedOntServiceId);
        })) {
            state.selectedOntServiceId = ALL_OPTION;
        }
    }

    function renderControls() {
        renderServerControl();
        renderFiberControl();
        renderOntControl();
        populateNodeServerOptions();
        scheduleMapCardHeightSync();
    }

    function initLayoutSync() {
        scheduleMapCardHeightSync();

        if (!state.dom.sidePanel || typeof ResizeObserver !== 'function') {
            return;
        }

        state.sidePanelResizeObserver = new ResizeObserver(function() {
            scheduleMapCardHeightSync();
        });
        state.sidePanelResizeObserver.observe(state.dom.sidePanel);
    }

    function scheduleMapCardHeightSync() {
        if (state.layoutSyncFrame) {
            window.cancelAnimationFrame(state.layoutSyncFrame);
        }

        state.layoutSyncFrame = window.requestAnimationFrame(function() {
            state.layoutSyncFrame = null;
            syncMapCardHeight();
        });
    }

    function syncMapCardHeight() {
        if (!state.dom.mapCard) {
            return;
        }

        if (window.innerWidth <= 1024) {
            state.dom.mapCard.style.height = '';
            state.dom.mapCard.style.minHeight = '';
            if (state.map) {
                state.map.invalidateSize();
            }
            return;
        }

        const sidePanelHeight = state.dom.sidePanel
            ? state.dom.sidePanel.getBoundingClientRect().height
            : 0;
        if (!sidePanelHeight) {
            return;
        }

        const targetHeight = Math.max(320, Math.round(sidePanelHeight));
        state.dom.mapCard.style.height = targetHeight + 'px';
        state.dom.mapCard.style.minHeight = targetHeight + 'px';

        if (state.map) {
            window.requestAnimationFrame(function() {
                state.map.invalidateSize();
            });
        }
    }

    function renderServerControl() {
        const items = state.data.servers || [];
        if (items.length <= 1) {
            state.dom.serverSelect.style.display = 'none';
            state.dom.serverSingle.style.display = 'flex';
            if (items.length === 1) {
                state.dom.serverSingle.querySelector('span').textContent = items[0].name || 'Server';
            } else {
                state.dom.serverSingle.querySelector('span').textContent = 'Belum ada company server';
            }
            return;
        }

        const html = items.map(function(item) {
            const selected = String(item.id) === String(state.selectedServerId) ? ' selected' : '';
            const disabled = item.hasCoordinates ? '' : ' disabled';
            const label = item.name || 'Server';
            return '<option value="' + escapeHtml(String(item.id)) + '"' + selected + disabled + '>'
                + escapeHtml(label)
                + '</option>';
        }).join('');

        state.dom.serverSelect.innerHTML = html;
        state.dom.serverSelect.style.display = 'block';
        state.dom.serverSingle.style.display = 'none';
    }

    function populateNodeServerOptions() {
        const items = state.data.servers || [];
        const html = items.map(function(item) {
            const selected = String(item.id) === String(state.selectedServerId) ? ' selected' : '';
            return '<option value="' + escapeHtml(String(item.id)) + '"' + selected + '>'
                + escapeHtml(item.name || 'Server')
                + '</option>';
        }).join('');

        state.dom.nodeServerInput.innerHTML = html || '<option value="">Belum ada server/company</option>';
    }

    function renderFiberControl() {
        const items = (state.data.fiberNodes || []).slice().sort(compareByName);
        let html = '<option value="' + ALL_OPTION + '">Tampilkan semua ODP/ODC</option>';

        const odcOptions = items.filter(function(item) { return item.type === 'ODC'; });
        const odpOptions = items.filter(function(item) { return item.type === 'ODP'; });

        if (odcOptions.length > 0) {
            html += '<optgroup label="ODC">';
            html += odcOptions.map(function(item) {
                const key = buildNodeKey(item.type, item.id);
                const selected = key === state.selectedNodeKey ? ' selected' : '';
                return '<option value="' + escapeHtml(key) + '"' + selected + '>'
                    + escapeHtml(buildNodeOptionLabel(item))
                    + '</option>';
            }).join('');
            html += '</optgroup>';
        }

        if (odpOptions.length > 0) {
            html += '<optgroup label="ODP">';
            html += odpOptions.map(function(item) {
                const key = buildNodeKey(item.type, item.id);
                const selected = key === state.selectedNodeKey ? ' selected' : '';
                return '<option value="' + escapeHtml(key) + '"' + selected + '>'
                    + escapeHtml(buildNodeOptionLabel(item))
                    + '</option>';
            }).join('');
            html += '</optgroup>';
        }

        state.dom.nodeFilter.innerHTML = html;
        state.dom.nodeFilter.value = state.selectedNodeKey || ALL_OPTION;
    }

    function renderOntControl() {
        const items = (state.data.onts || []).slice().sort(compareByName);
        let html = '<option value="' + ALL_OPTION + '">Tampilkan semua ONT</option>';
        html += items.map(function(item) {
            const selected = String(item.serviceId) === String(state.selectedOntServiceId) ? ' selected' : '';
            return '<option value="' + escapeHtml(String(item.serviceId)) + '"' + selected + '>'
                + escapeHtml(buildOntOptionLabel(item))
                + '</option>';
        }).join('');

        state.dom.ontFilter.innerHTML = html;
        state.dom.ontFilter.value = state.selectedOntServiceId || ALL_OPTION;
    }

    function renderSummary() {
        state.dom.serverCount.textContent = String((state.data.servers || []).length);
        state.dom.fiberCount.textContent = String((state.data.fiberNodes || []).length);
        state.dom.ontCount.textContent = String((state.data.onts || []).length);
        state.dom.linkCount.textContent = String((state.data.links || []).length);
    }

    function renderToolbarState() {
        setToolbarEditState(Boolean(state.data.canEdit));

        if (state.drawMode) {
            state.dom.connectButton.classList.add('is-active');
            state.dom.connectButton.innerHTML = '<i class="fas fa-link"></i> Mode Hubungkan Aktif';
            state.dom.toolbarNote.textContent = state.pendingLinkStart
                ? 'Titik awal dipilih. Gerakkan kursor lalu klik marker tujuan untuk menyimpan garis.'
                : 'Mode hubungkan aktif. Klik marker titik awal lalu titik tujuan.';
        } else {
            state.dom.connectButton.classList.remove('is-active');
            state.dom.connectButton.innerHTML = '<i class="fas fa-share-nodes"></i> Mode Hubungkan';
            state.dom.toolbarNote.textContent = '';
        }

        state.dom.deleteLineButton.disabled = !state.data.canEdit || !state.selectedLinkId;
    }

    function setToolbarEditState(canEdit) {
        state.dom.connectButton.style.display = canEdit ? '' : 'none';
        state.dom.cancelButton.style.display = canEdit ? '' : 'none';
        state.dom.deleteLineButton.style.display = canEdit ? '' : 'none';
        state.dom.addNodeButton.style.display = canEdit ? '' : 'none';
    }

    function renderAll(fitToBounds) {
        if (!state.map) {
            return;
        }

        clearLayer(state.layers.server);
        clearLayer(state.layers.fiber);
        clearLayer(state.layers.ont);
        clearLayer(state.layers.link);
        state.nodeIndex = buildNodeIndex();

        const visibleNodes = {};
        renderServerMarker(visibleNodes);
        renderFiberMarkers(visibleNodes);
        renderOntMarkers(visibleNodes);
        renderLines(visibleNodes);

        const visibleLatLngs = Object.values(visibleNodes).map(function(item) {
            return item.latlng;
        });

        if (visibleLatLngs.length === 0) {
            showEmptyState('Tidak ada koordinat yang cocok dengan filter saat ini.');
        } else {
            hideEmptyState();
        }

        if (fitToBounds) {
            fitMap(visibleLatLngs);
        }
    }

    function renderServerMarker(visibleNodes) {
        const selectedServer = getSelectedServer();
        if (!selectedServer || !selectedServer.hasCoordinates) {
            return;
        }

        const latlng = [Number(selectedServer.latitude), Number(selectedServer.longitude)];
        const marker = L.marker(latlng, { icon: state.icons.SERVER });
        marker.bindPopup(
            '<div class="mapping-popup"><strong>' + escapeHtml(selectedServer.name || 'Server') + '</strong>'
            + '<div>' + escapeHtml(selectedServer.address || 'Alamat server belum diisi') + '</div>'
            + '<div style="margin-top: 6px; color: #94a3b8;">' + escapeHtml(selectedServer.coordinatesText || '-') + '</div></div>'
        );

            marker.on('mouseover', function() {
                if (isPersistentDetailActive()) {
                    return;
                }
                state.hoveredDetailKey = buildNodeKey('SERVER', selectedServer.id);
                renderServerDetail(selectedServer);
            });

        marker.on('mouseout', function() {
            clearHoveredDetail(buildNodeKey('SERVER', selectedServer.id));
        });

        marker.on('click', function() {
            if (state.drawMode) {
                handleDrawNode('SERVER', selectedServer.id, marker.getLatLng());
                return;
            }
            state.persistentOntServiceId = null;
            state.persistentDetailKey = buildNodeKey('SERVER', selectedServer.id);
            state.hoveredDetailKey = state.persistentDetailKey;
            renderServerDetail(selectedServer, true);
        });

        marker.addTo(state.layers.server);
        visibleNodes[buildNodeKey('SERVER', selectedServer.id)] = {
            latlng: marker.getLatLng(),
            label: selectedServer.name || 'Server'
        };
    }

    function renderFiberMarkers(visibleNodes) {
        getVisibleFiberNodes().forEach(function(node) {
            const latlng = [Number(node.latitude), Number(node.longitude)];
            const marker = L.marker(latlng, { icon: state.icons[node.type] || state.icons.ODP });
            marker.bindPopup(buildFiberPopup(node));
            marker.on('mouseover', function() {
                if (isPersistentDetailActive()) {
                    return;
                }
                state.hoveredDetailKey = buildNodeKey(node.type, node.id);
                renderFiberDetail(node);
            });
            marker.on('mouseout', function() {
                clearHoveredDetail(buildNodeKey(node.type, node.id));
            });
            marker.on('click', function() {
                if (state.drawMode) {
                    handleDrawNode(node.type, node.id, marker.getLatLng());
                } else if (['ODC', 'ODP'].includes(node.type)) {
                    state.persistentOntServiceId = null;
                    state.persistentDetailKey = buildNodeKey(node.type, node.id);
                    state.hoveredDetailKey = state.persistentDetailKey;
                    renderFiberDetail(node, true);
                }
            });
            marker.addTo(state.layers.fiber);
            visibleNodes[buildNodeKey(node.type, node.id)] = {
                latlng: marker.getLatLng(),
                label: node.name || node.type
            };
        });
    }

    function renderOntMarkers(visibleNodes) {
        getVisibleOnts().forEach(function(ont) {
            const latlng = [Number(ont.latitude), Number(ont.longitude)];
            const marker = L.marker(latlng, { icon: state.icons.ONT });
            marker.bindPopup(buildOntPopup(ont));

            marker.on('mouseover', function() {
                if (isPersistentDetailActive()) {
                    return;
                }
                state.hoveredDetailKey = buildNodeKey('ONT', ont.serviceId);
                loadOntDetail(ont.serviceId);
            });

            marker.on('mouseout', function() {
                clearHoveredDetail(buildNodeKey('ONT', ont.serviceId));
            });

            marker.on('click', function() {
                if (state.drawMode) {
                    handleDrawNode('ONT', ont.serviceId, marker.getLatLng());
                } else {
                    state.persistentOntServiceId = ont.serviceId;
                    state.persistentDetailKey = null;
                    state.hoveredDetailKey = buildNodeKey('ONT', ont.serviceId);
                    loadOntDetail(ont.serviceId);
                }
            });

            marker.addTo(state.layers.ont);
            visibleNodes[buildNodeKey('ONT', ont.serviceId)] = {
                latlng: marker.getLatLng(),
                label: ont.customerName || 'ONT'
            };
        });
    }

    function renderLines(forcedVisibleNodes) {
        if (!state.layers.link) {
            return;
        }

        clearLayer(state.layers.link);
        const visibleNodes = forcedVisibleNodes || buildVisibleNodeIndexFromLayers();

        (state.data.links || []).forEach(function(link) {
            const fromNode = visibleNodes[buildNodeKey(link.fromType, link.fromId)];
            const toNode = visibleNodes[buildNodeKey(link.toType, link.toId)];
            if (!fromNode || !toNode) {
                return;
            }

            const isSelected = String(link.id) === String(state.selectedLinkId);
            const polyline = L.polyline([fromNode.latlng, toNode.latlng], {
                color: link.lineColor || '#fb923c',
                weight: isSelected ? 6 : 4,
                opacity: isSelected ? 1 : 0.88
            });

            polyline.bindTooltip(
                escapeHtml((fromNode.label || link.fromType) + ' -> ' + (toNode.label || link.toType)),
                { sticky: true }
            );

            polyline.on('click', function(event) {
                if (event.originalEvent) {
                    event.originalEvent.preventDefault();
                    event.originalEvent.stopPropagation();
                }
                state.selectedLinkId = link.id;
                renderToolbarState();
                renderLines(visibleNodes);
                setStatus('Garis dipilih. Tekan tombol hapus jika ingin menghapusnya.', 'info');
            });

            polyline.addTo(state.layers.link);
        });

        if (state.drawMode && state.pendingLinkStart) {
            state.previewLine = L.polyline([state.pendingLinkStart.latlng, state.pendingLinkStart.latlng], {
                color: '#f8fafc',
                weight: 3,
                opacity: 0.9,
                dashArray: '8 8'
            }).addTo(state.layers.link);
        }
    }

    function handleDrawNode(type, id, latlng) {
        if (!state.drawMode) {
            return;
        }

        const node = {
            type: type,
            id: String(id),
            latlng: latlng
        };

        if (!state.pendingLinkStart) {
            state.pendingLinkStart = node;
            renderLines();
            renderToolbarState();
            setStatus('Titik awal dipilih. Sekarang pilih titik tujuan.', 'info');
            return;
        }

        if (state.pendingLinkStart.type === node.type && state.pendingLinkStart.id === node.id) {
            setStatus('Pilih node tujuan yang berbeda dari titik awal.', 'warning');
            return;
        }

        saveLink(state.pendingLinkStart, node);
    }

    async function saveLink(startNode, endNode) {
        try {
            const payload = await fetchJson('/api/mapping/links', {
                method: 'POST',
                body: JSON.stringify({
                    fromType: startNode.type,
                    fromId: Number(startNode.id),
                    toType: endNode.type,
                    toId: Number(endNode.id)
                })
            });

            const existingIndex = (state.data.links || []).findIndex(function(item) {
                return String(item.id) === String(payload.id);
            });

            if (existingIndex >= 0) {
                state.data.links[existingIndex] = payload;
            } else {
                state.data.links.push(payload);
            }

            state.selectedLinkId = payload.id;
            renderSummary();
            clearPendingLink();
            renderToolbarState();
            renderAll(false);
            setStatus('Garis topologi berhasil disimpan.', 'success');
        } catch (error) {
            console.error('Failed to save link:', error);
            setStatus(error.message || 'Gagal menyimpan garis topologi.', 'error');
        }
    }

    async function deleteSelectedLine() {
        try {
            await fetchJson('/api/mapping/links/' + encodeURIComponent(String(state.selectedLinkId)), {
                method: 'DELETE'
            });

            state.data.links = (state.data.links || []).filter(function(item) {
                return String(item.id) !== String(state.selectedLinkId);
            });
            state.selectedLinkId = null;
            renderSummary();
            renderToolbarState();
            renderAll(false);
            setStatus('Garis topologi berhasil dihapus.', 'success');
        } catch (error) {
            console.error('Failed to delete topology link:', error);
            setStatus(error.message || 'Gagal menghapus garis topologi.', 'error');
        }
    }

    function clearPendingLink() {
        state.pendingLinkStart = null;
        state.previewLine = null;
        clearLayer(state.layers.link);
    }

    async function loadOntDetail(serviceId) {
        try {
            const cacheKey = String(serviceId);
            if (!state.detailCache.has(cacheKey)) {
                const detail = await fetchJson('/api/mapping/onts/' + encodeURIComponent(cacheKey));
                state.detailCache.set(cacheKey, detail);
            }

            if (state.hoveredDetailKey !== buildNodeKey('ONT', cacheKey)) {
                return;
            }

            const detail = state.detailCache.get(cacheKey);
            renderOntDetail(detail);
        } catch (error) {
            console.error('Failed to load ONT detail:', error);
            renderOntDetailError(error.message || 'Detail ONT gagal dimuat.');
        }
    }

    function renderOntDetail(detail) {
        const statusClass = mapStatusClass(detail.status);
        let html = ''
            + '<div class="mapping-detail-head">'
            + '  <div>'
            + '    <h3 class="mapping-detail-title">' + escapeHtml(detail.customerName || 'ONT') + '</h3>'
            + '    <p class="mapping-detail-subtitle">' + escapeHtml(detail.customerCode || '-') + ' - ' + escapeHtml(detail.serverName || '-') + '</p>'
            + '  </div>'
            + '  <span class="mapping-badge ' + statusClass + '">' + escapeHtml(detail.status || 'unknown') + '</span>'
            + '</div>'
            + '<div class="mapping-detail-grid">'
            + detailItem('Redaman ONT', detail.ontRedaman)
            + detailItem('Durasi Standby', detail.ontStandbyDuration)
            + detailItem('Nama WiFi', detail.wifiName)
            + detailItem('Password WiFi', detail.wifiPassword)
            + detailItem('ODP', detail.odpName)
            + detailItem('ODC', detail.odcName)
            + detailItem('PPPoE', detail.pppoeUsername)
            + detailItem('IP Address', detail.ipAddress)
            + detailItem('Serial ONT', detail.ontSerial)
            + detailItem('Brand ONT', detail.ontBrand)
            + detailItem('Restart Terakhir', detail.lastRestartRequestedAt)
            + '</div>';

        if (state.data.canEdit) {
            html += '<div class="mapping-detail-actions">'
                + '<button id="mappingRestartOntButton" class="btn btn-primary" type="button"><i class="fas fa-power-off"></i> Restart ONT</button>'
                + '</div>'
                + '<form id="mappingWifiForm" class="mapping-inline-form">'
                + '  <div class="mapping-inline-form-title">Ganti Nama WiFi & Password</div>'
                + '  <input id="mappingWifiNameInput" class="mapping-input" type="text" value="' + escapeAttribute(detail.wifiName === 'Belum diatur' ? '' : detail.wifiName) + '" placeholder="Nama WiFi">'
                + '  <input id="mappingWifiPasswordInput" class="mapping-input" type="text" value="' + escapeAttribute(detail.wifiPassword === 'Belum diatur' ? '' : detail.wifiPassword) + '" placeholder="Password WiFi">'
                + '  <div class="mapping-inline-form-actions">'
                + '      <button class="btn btn-primary" type="submit"><i class="fas fa-floppy-disk"></i> Simpan WiFi</button>'
                + '  </div>'
                + '</form>';
        }

        state.dom.detail.innerHTML = html;

        if (state.data.canEdit) {
            const restartButton = document.getElementById('mappingRestartOntButton');
            const wifiForm = document.getElementById('mappingWifiForm');

            restartButton.addEventListener('click', function() {
                restartOnt(detail.serviceId);
            });

            wifiForm.addEventListener('submit', function(event) {
                event.preventDefault();
                const wifiName = document.getElementById('mappingWifiNameInput').value;
                const wifiPassword = document.getElementById('mappingWifiPasswordInput').value;
                updateWifi(detail.serviceId, wifiName, wifiPassword);
            });
        }
    }

    function renderOntDetailError(message) {
        state.dom.detail.innerHTML = '<div class="mapping-detail-head">'
            + '<div><h3 class="mapping-detail-title">Detail ONT</h3><p class="mapping-detail-subtitle">Gagal mengambil data.</p></div>'
            + '</div>'
            + '<div class="mapping-detail-item"><div class="mapping-detail-item-label">Error</div><div class="mapping-detail-item-value">'
            + escapeHtml(message || 'Terjadi kesalahan')
            + '</div></div>';
    }

    function renderServerDetail(server, showActions) {
        let html = '<div class="mapping-detail-head">'
            + '<div>'
            + '<h3 class="mapping-detail-title">' + escapeHtml(server.name || 'Server / Company') + '</h3>'
            + '<p class="mapping-detail-subtitle">Detail server / company pada titik koordinat peta.</p>'
            + '</div>'
            + '<span class="mapping-badge active">SERVER</span>'
            + '</div>'
            + '<div class="mapping-detail-grid">'
            + detailItem('Nama', server.name)
            + detailItem('Alamat', server.address)
            + detailItem('Koordinat', server.coordinatesText)
            + detailItem('Status Koordinat', server.hasCoordinates ? 'Tersedia' : 'Belum diatur')
            + '</div>';

        if (showActions && state.data.canEdit) {
            html += '<div class="mapping-detail-actions mapping-node-actions">'
                + `<button class="btn btn-primary server-edit-btn" data-id="${escapeAttribute(server.id)}"><i class="fas fa-edit"></i> Edit</button>`
                + `<button class="btn btn-danger server-delete-btn" data-id="${escapeAttribute(server.id)}"><i class="fas fa-trash"></i> Hapus</button>`
                + '</div>';
        }

        state.dom.detail.innerHTML = html;

        if (showActions && state.data.canEdit) {
            const editButton = state.dom.detail.querySelector('.server-edit-btn');
            const deleteButton = state.dom.detail.querySelector('.server-delete-btn');
            if (editButton) {
                editButton.addEventListener('click', function() {
                    loadServerForEdit(server.id);
                });
            }
            if (deleteButton) {
                deleteButton.addEventListener('click', async function() {
                    const confirmed = await window.nmxConfirm(`Hapus server "${server.name || server.id}"? (soft delete)`, {
                        title: 'Konfirmasi Hapus',
                        confirmText: 'Hapus',
                        confirmClass: 'btn btn-danger'
                    });
                    if (confirmed) {
                        deleteServerFromMap(server.id);
                    }
                });
            }
        }
    }

    function renderFiberDetail(node, showActions) {
        let html = '<div class="mapping-detail-head">'
            + '<div>'
            + '<h3 class="mapping-detail-title">' + escapeHtml(node.name || node.type || 'Node Fiber') + '</h3>'
            + '<p class="mapping-detail-subtitle">Detail ' + escapeHtml(node.type || 'node') + ' pada topologi fiber.</p>'
            + '</div>'
            + '<span class="mapping-badge active">' + escapeHtml(node.type || 'NODE') + '</span>'
            + '</div>'
            + '<div class="mapping-detail-grid">'
            + detailItem('Kode', node.code)
            + detailItem('Server', node.serverName)
            + detailItem('Parent', node.parentName)
            + detailItem('Splitter', node.splitter)
            + detailItem('Lokasi', node.location)
            + detailItem('Koordinat', [node.latitude, node.longitude].filter(Boolean).join(', '))
            + '</div>';

        if (showActions && state.data.canEdit) {
            html += '<div class="mapping-detail-actions mapping-node-actions">'
                + `<button class="btn btn-primary node-edit-btn" data-id="${escapeAttribute(node.id)}"><i class="fas fa-edit"></i> Edit</button>`
                + `<button class="btn btn-danger node-delete-btn" data-id="${escapeAttribute(node.id)}"><i class="fas fa-trash"></i> Hapus</button>`
                + '</div>';
        }

        state.dom.detail.innerHTML = html;

        if (showActions && state.data.canEdit) {
            const editButton = state.dom.detail.querySelector('.node-edit-btn');
            const deleteButton = state.dom.detail.querySelector('.node-delete-btn');

            if (editButton) {
                editButton.addEventListener('click', function() {
                    loadNodeForEdit(node.id, true);
                });
            }
            if (deleteButton) {
                deleteButton.addEventListener('click', async function() {
                    const confirmed = await window.nmxConfirm(`Hapus ${node.type} "${node.name}"? (soft delete)`, {
                        title: 'Konfirmasi Hapus',
                        confirmText: 'Hapus',
                        confirmClass: 'btn btn-danger'
                    });
                    if (confirmed) {
                        deleteNode(node.id, node.type);
                    }
                });
            }
        }
    }

    function resetOntDetail() {
        state.dom.detail.innerHTML = '<div class="mapping-detail-head">'
            + '<div>'
            + '<h3 class="mapping-detail-title">Detail Marker</h3>'
            + '<p class="mapping-detail-subtitle">Arahkan kursor ke pin server, ODC, ODP, atau ONT untuk melihat detail.</p>'
            + '</div>'
            + '</div>'
            + '<div class="mapping-detail-item">'
            + '<div class="mapping-detail-item-label">Informasi</div>'
            + '<div class="mapping-detail-item-value">Belum ada marker yang sedang disentuh cursor.</div>'
            + '</div>';
    }

    function clearHoveredDetail(expectedKey) {
        if (isPersistentDetailActive()) {
            return;
        }
        if (!expectedKey || state.hoveredDetailKey === expectedKey) {
            state.hoveredDetailKey = null;
            resetOntDetail();
        }
    }

    function isPersistentDetailActive() {
        return Boolean(state.persistentOntServiceId || state.persistentDetailKey);
    }

    function closePersistentDetail() {
        state.persistentOntServiceId = null;
        state.persistentDetailKey = null;
        state.hoveredDetailKey = null;
        resetOntDetail();
    }

    function shouldIgnoreDetailDismissal(target) {
        if (!target) {
            return false;
        }
        if (state.dom.detail && state.dom.detail.contains(target)) {
            return true;
        }
        if (target.closest && (target.closest('.leaflet-marker-icon') || target.closest('.leaflet-popup'))) {
            return true;
        }
        if (state.dom.nodeModal && state.dom.nodeModal.contains(target)) {
            return true;
        }
        if (state.dom.serverModal && state.dom.serverModal.contains(target)) {
            return true;
        }
        return false;
    }

    async function restartOnt(serviceId) {
        try {
            const detail = await fetchJson('/api/mapping/onts/' + encodeURIComponent(String(serviceId)) + '/restart', {
                method: 'POST'
            });
            state.detailCache.set(String(serviceId), detail);
            renderOntDetail(detail);
            setStatus('Permintaan restart ONT berhasil disimpan.', 'success');
        } catch (error) {
            console.error('Failed to restart ONT:', error);
            setStatus(error.message || 'Gagal menyimpan aksi restart ONT.', 'error');
        }
    }

    async function updateWifi(serviceId, wifiName, wifiPassword) {
        try {
            const detail = await fetchJson('/api/mapping/onts/' + encodeURIComponent(String(serviceId)) + '/wifi', {
                method: 'PUT',
                body: JSON.stringify({
                    wifiName: wifiName,
                    wifiPassword: wifiPassword
                })
            });
            state.detailCache.set(String(serviceId), detail);
            renderOntDetail(detail);
            setStatus('Nama WiFi dan password berhasil diperbarui.', 'success');
        } catch (error) {
            console.error('Failed to update WiFi:', error);
            setStatus(error.message || 'Gagal memperbarui WiFi ONT.', 'error');
        }
    }

    function openNodeModal() {
        if (!state.data.servers || state.data.servers.length === 0) {
            setStatus('Tambahkan data company/server terlebih dahulu sebelum membuat ODP/ODC.', 'warning');
            return;
        }

        clearPendingLink();
        state.drawMode = false;
        renderToolbarState();

        state.dom.nodeForm.reset();
        state.dom.nodeTypeInput.value = 'ODP';
        state.dom.nodeServerInput.value = state.selectedServerId || (state.data.servers[0] ? String(state.data.servers[0].id) : '');
        state.dom.nodeCoordinatesInput.value = '';
        state.dom.nodeModal.querySelector('.mapping-modal-title').textContent = 'Tambah ODP/ODC';
        state.dom.nodeModal.querySelector('.mapping-modal-subtitle').textContent = 'Klik lokasi pada peta untuk mengisi titik koordinat otomatis.';
        updateNodeSaveButtonLabel('create');
        clearCoordinatePickerMarker();
        state.editMode = false;
        state.editingNodeId = null;

        state.dom.nodeModalOverlay.classList.add('open');
        state.dom.nodeModal.classList.add('open');
        syncDetailCardVisibility();
        setStatus('Form tambah ODP/ODC aktif. Klik peta untuk mengisi koordinat.', 'info');
    }

    function openServerModalForEdit(server, mikrotikDevices) {
        state.editingServerId = server.id;
        state.mikrotikOptions = Array.isArray(mikrotikDevices) ? mikrotikDevices : [];
        renderServerMikrotikOptions(server.mikrotikId);

        state.dom.serverNameInput.value = server.name || '';
        state.dom.serverLocationInput.value = server.location || '';
        state.dom.serverCoordinatesInput.value = server.latitude != null && server.longitude != null
            ? `${server.latitude}, ${server.longitude}`
            : '';
        applyServerCoordinatesText(state.dom.serverCoordinatesInput.value);

        const selectedMikrotik = state.mikrotikOptions.find(function(item) {
            return String(item.id) === String(server.mikrotikId || '');
        });
        state.dom.serverStatusInput.value = selectedMikrotik ? formatMikrotikStatus(selectedMikrotik.status) : '';
        state.dom.serverIpInput.value = selectedMikrotik ? (selectedMikrotik.ipAddress || '') : (server.ipAddress || '');

        state.dom.serverModal.querySelector('.mapping-modal-title').textContent = 'Edit Server';
        state.dom.serverModal.querySelector('.mapping-modal-subtitle').textContent = 'Perbarui data server/company pada peta.';

        state.dom.serverModalOverlay.classList.add('open');
        state.dom.serverModal.classList.add('open');
        syncDetailCardVisibility();
    }

    function closeServerModal() {
        state.dom.serverModalOverlay.classList.remove('open');
        state.dom.serverModal.classList.remove('open');
        state.editingServerId = null;
        syncDetailCardVisibility();
    }

    function isServerModalOpen() {
        return state.dom.serverModal.classList.contains('open');
    }

    async function loadServerForEdit(id) {
        try {
            const [server, mikrotikDevices] = await Promise.all([
                fetchJson(`/api/servers/${id}`),
                fetchJson('/api/network/mikrotik?ts=' + Date.now())
            ]);
            openServerModalForEdit(server, mikrotikDevices);
        } catch (error) {
            console.error('Failed to load server detail:', error);
            setStatus(error.message || 'Gagal memuat data server.', 'error');
        }
    }

    function renderServerMikrotikOptions(selectedId) {
        const options = state.mikrotikOptions.map(function(item) {
            return `<option value="${escapeAttribute(item.id)}">${escapeHtml(item.name || item.location || '-') }</option>`;
        }).join('');
        state.dom.serverMikrotikInput.innerHTML = '<option value="">-- Pilih Mikrotik --</option>' + options;
        if (selectedId) {
            state.dom.serverMikrotikInput.value = String(selectedId);
        }
    }

    function updateServerMikrotikMetadata() {
        const selectedId = state.dom.serverMikrotikInput.value;
        const selected = state.mikrotikOptions.find(function(item) {
            return String(item.id) === String(selectedId);
        });
        state.dom.serverStatusInput.value = selected ? formatMikrotikStatus(selected.status) : '';
        state.dom.serverIpInput.value = selected ? (selected.ipAddress || '') : '';
    }

    function formatMikrotikStatus(status) {
        const normalized = String(status || '').toLowerCase();
        if (normalized === 'online') {
            return 'Online';
        }
        if (normalized === 'maintenance') {
            return 'Maintenance';
        }
        if (normalized === 'offline') {
            return 'Offline';
        }
        return status ? String(status) : '-';
    }

    function applyServerCoordinatesText(value) {
        const parsed = parseCoordinateText(value);
        if (!parsed) {
            state.dom.serverLatitudeInput.value = '';
            state.dom.serverLongitudeInput.value = '';
            return;
        }
        state.dom.serverLatitudeInput.value = String(parsed.latitude);
        state.dom.serverLongitudeInput.value = String(parsed.longitude);
    }

    async function saveServerForm() {
        if (!state.editingServerId) {
            return;
        }

        const name = state.dom.serverNameInput.value.trim();
        const mikrotikId = state.dom.serverMikrotikInput.value ? Number(state.dom.serverMikrotikInput.value) : null;
        const location = state.dom.serverLocationInput.value.trim();
        const coordinates = parseCoordinateText(state.dom.serverCoordinatesInput.value);

        if (!name) {
            setStatus('Nama server wajib diisi.', 'warning');
            return;
        }
        if (!mikrotikId) {
            setStatus('Mikrotik server wajib dipilih.', 'warning');
            return;
        }

        try {
            const payload = await fetchJson(`/api/servers/${state.editingServerId}`, {
                method: 'PUT',
                body: JSON.stringify({
                    name: name,
                    mikrotikId: mikrotikId,
                    location: location || null,
                    latitude: coordinates ? coordinates.latitude : null,
                    longitude: coordinates ? coordinates.longitude : null
                })
            });
            closeServerModal();
            setStatus('Server berhasil diperbarui.', 'success');
            await loadOverview(false);
        } catch (error) {
            console.error('Failed to update server:', error);
            setStatus(error.message || 'Gagal memperbarui server.', 'error');
        }
    }

    async function deleteServerFromMap(id) {
        try {
            await fetchJson(`/api/servers/${id}`, { method: 'DELETE' });
            setStatus('Server berhasil dinonaktifkan.', 'success');
            await loadOverview(false);
        } catch (error) {
            console.error('Failed to delete server:', error);
            setStatus(error.message || 'Gagal menghapus server.', 'error');
        }
    }

    function openNodeModalForEdit(node) {
        state.dom.nodeTypeInput.value = node.type;
        state.dom.nodeServerInput.value = node.serverName ? state.data.servers.find(s => s.name === node.serverName)?.id || '' : '';
        state.dom.nodeNameInput.value = node.name || '';
        state.dom.nodeSplitterInput.value = node.splitter || '';
        state.dom.nodeCoordinatesInput.value = `${node.latitude}, ${node.longitude}`;
        state.dom.nodeModal.querySelector('.mapping-modal-title').textContent = `Edit ${node.type}`;
        state.dom.nodeModal.querySelector('.mapping-modal-subtitle').textContent = 'Update data ODP/ODC.';
        updateNodeSaveButtonLabel('update');
        clearCoordinatePickerMarker();
        state.editMode = true;
        state.editingNodeId = node.id;

        state.dom.nodeModalOverlay.classList.add('open');
        state.dom.nodeModal.classList.add('open');
        syncDetailCardVisibility();
        setStatus(`Edit mode untuk ${node.type} ${node.name || node.id}`, 'info');
    }

    function closeNodeModal() {
        state.dom.nodeModalOverlay.classList.remove('open');
        state.dom.nodeModal.classList.remove('open');
        clearCoordinatePickerMarker();
        syncDetailCardVisibility();
    }

    function isNodeModalOpen() {
        return state.dom.nodeModal.classList.contains('open');
    }

    function syncDetailCardVisibility() {
        if (!state.dom.detail) {
            return;
        }
        state.dom.detail.classList.toggle('is-hidden', isNodeModalOpen() || isServerModalOpen());
    }

    function applyPickedCoordinates(latlng) {
        if (!latlng) {
            return;
        }

        const latitude = Number(latlng.lat).toFixed(8);
        const longitude = Number(latlng.lng).toFixed(8);
        state.dom.nodeCoordinatesInput.value = latitude + ', ' + longitude;

        clearCoordinatePickerMarker();
        state.coordinatePickerMarker = L.marker([latlng.lat, latlng.lng], {
            icon: state.icons[state.dom.nodeTypeInput.value] || state.icons.ODP
        }).addTo(state.map);
    }

    function clearCoordinatePickerMarker() {
        if (state.coordinatePickerMarker && state.map) {
            state.map.removeLayer(state.coordinatePickerMarker);
        }
        state.coordinatePickerMarker = null;
    }

    async function saveNodeForm() {
        const coordinates = parseCoordinateText(state.dom.nodeCoordinatesInput.value);
        if (!state.dom.nodeServerInput.value) {
            setStatus('Pilih server/company terlebih dahulu.', 'warning');
            return;
        }
        if (!state.dom.nodeNameInput.value.trim()) {
            setStatus('Nama ODP/ODC wajib diisi.', 'warning');
            return;
        }
        if (!coordinates) {
            setStatus('Koordinat wajib diisi.', 'warning');
            return;
        }

        try {
            let payload;
            if (state.editMode && state.editingNodeId) {
                payload = await fetchJson(`/api/mapping/nodes/${state.editingNodeId}`, {
                    method: 'PUT',
                    body: JSON.stringify({
                        name: state.dom.nodeNameInput.value.trim(),
                        splitter: state.dom.nodeSplitterInput.value.trim(),
                        latitude: coordinates.latitude,
                        longitude: coordinates.longitude,
                        companyProfileId: Number(state.dom.nodeServerInput.value)
                    })
                });
                setStatus('Data berhasil diupdate.', 'success');
            } else {
                payload = await fetchJson('/api/mapping/nodes', {
                    method: 'POST',
                    body: JSON.stringify({
                        nodeType: state.dom.nodeTypeInput.value,
                        companyProfileId: Number(state.dom.nodeServerInput.value),
                        name: state.dom.nodeNameInput.value.trim(),
                        splitter: state.dom.nodeSplitterInput.value.trim(),
                        latitude: coordinates.latitude,
                        longitude: coordinates.longitude
                    })
                });
                setStatus('Data ' + state.dom.nodeTypeInput.value + ' berhasil ditambahkan.', 'success');
            }

            state.selectedNodeKey = buildNodeKey(payload.type, payload.id);
            closeNodeModal();
            if (state.editMode) {
                state.lastUpdatedNode = payload;
                state.data.fiberNodes = (state.data.fiberNodes || []).map(function(item) {
                    if (String(item.id) === String(payload.id) && String(item.type) === String(payload.type)) {
                        return payload;
                    }
                    return item;
                });
                renderAll(false);
            }
            await loadOverview(false);
            fitMap([L.latLng(coordinates.latitude, coordinates.longitude)]);
        } catch (error) {
            console.error('Failed to save node:', error);
            setStatus(error.message || 'Gagal menyimpan.', 'error');
        }
    }

    function updateNodeSaveButtonLabel(mode = 'create') {
        if (!state.dom.nodeSaveButton) {
            return;
        }
        const nodeType = String(state.dom.nodeTypeInput.value || 'ODP').toUpperCase();
        const dbName = resolveNodeDatabaseName(nodeType).toUpperCase();
        if (mode === 'update') {
            state.dom.nodeSaveButton.innerHTML = `<i class="fas fa-floppy-disk"></i> Update ${dbName}`;
        } else {
            state.dom.nodeSaveButton.innerHTML = `<i class="fas fa-floppy-disk"></i> Simpan ke ${dbName}`;
        }
    }

    async function loadNodeForEdit(id, openModal) {
        try {
            state.editingNodeId = id;
            const node = await fetchJson(`/api/mapping/nodes/${id}`);
            if (openModal) {
                openNodeModalForEdit(node);
            } else {
                renderNodeDetail(node);
            }
        } catch (error) {
            console.error('Failed to load node:', error);
            setStatus(error.message || 'Gagal memuat data node', 'error');
        }
    }

    function renderNodeDetail(node) {
        const html = '<div class="mapping-detail-head">'
            + '<div>'
            + `<h3 class="mapping-detail-title">${escapeHtml(node.name || node.type)}</h3>`
            + `<p class="mapping-detail-subtitle">${escapeHtml(node.type)} - ${escapeHtml(node.location || 'tanpa lokasi')}</p>`
            + '</div>'
            + `<span class="mapping-badge active">${escapeHtml(node.type)}</span>`
            + '</div>'
            + '<div class="mapping-detail-grid">'
            + detailItem('Kode', node.code)
            + detailItem('Server', node.serverName)
            + detailItem('Parent', node.parentName)
            + detailItem('Splitter', node.splitter)
            + detailItem('Lokasi', node.location)
            + detailItem('Koordinat', `${node.latitude}, ${node.longitude}`)
            + '</div>';

        if (state.data.canEdit) {
            html += '<div class="mapping-detail-actions mapping-node-actions">'
                + `<button class="btn btn-primary node-edit-btn" data-id="${node.id}"><i class="fas fa-edit"></i> Edit</button>`
                + `<button class="btn btn-danger node-delete-btn" data-id="${node.id}"><i class="fas fa-trash"></i> Hapus</button>`
                + '</div>';
        }

        state.dom.detail.innerHTML = html;

        if (state.data.canEdit) {
            document.querySelector('.node-edit-btn').addEventListener('click', function() {
                openNodeModalForEdit(node);
            });
            document.querySelector('.node-delete-btn').addEventListener('click', async function() {
                const confirmed = await window.nmxConfirm(`Hapus ${node.type} "${node.name}"? (soft delete)`, {
                    title: 'Konfirmasi Hapus',
                    confirmText: 'Hapus',
                    confirmClass: 'btn btn-danger'
                });
                if (confirmed) {
                    deleteNode(node.id, node.type);
                }
            });
        }
    }

    async function deleteNode(id, type) {
        try {
            await fetchJson(`/api/mapping/nodes/${id}`, { method: 'DELETE' });
            const normalizedType = type ? String(type).toUpperCase() : null;
            state.data.fiberNodes = (state.data.fiberNodes || []).filter(function(item) {
                if (String(item.id) !== String(id)) {
                    return true;
                }
                if (!normalizedType) {
                    return false;
                }
                return String(item.type).toUpperCase() !== normalizedType;
            });
            state.data.links = (state.data.links || []).filter(function(link) {
                const fromMatch = String(link.fromId) === String(id)
                    && (!normalizedType || String(link.fromType).toUpperCase() === normalizedType);
                const toMatch = String(link.toId) === String(id)
                    && (!normalizedType || String(link.toType).toUpperCase() === normalizedType);
                return !(fromMatch || toMatch);
            });
            if (normalizedType) {
                const key = buildNodeKey(normalizedType, id);
                if (state.selectedNodeKey === key) {
                    state.selectedNodeKey = ALL_OPTION;
                }
            }
            closePersistentDetail();
            renderAll(false);
            await loadOverview(false);
            setStatus('Node berhasil dihapus.', 'success');
        } catch (error) {
            console.error('Delete failed:', error);
            setStatus(error.message || 'Gagal hapus node.', 'error');
        }
    }

    function resolveNodeDatabaseName(nodeType) {
        return String(nodeType || '').toUpperCase() === 'ODC' ? 'odcs' : 'odps';
    }

    function parseCoordinateText(value) {
        if (!value) {
            return null;
        }

        const parts = String(value).split(',').map(function(item) {
            return item.trim();
        });
        if (parts.length < 2) {
            return null;
        }

        const latitude = Number(parts[0]);
        const longitude = Number(parts[1]);
        if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
            return null;
        }

        return {
            latitude: latitude,
            longitude: longitude
        };
    }

    function buildVisibleNodeIndexFromLayers() {
        const visibleNodes = {};
        const selectedServer = getSelectedServer();
        if (selectedServer && selectedServer.hasCoordinates) {
            visibleNodes[buildNodeKey('SERVER', selectedServer.id)] = {
                latlng: L.latLng(Number(selectedServer.latitude), Number(selectedServer.longitude)),
                label: selectedServer.name || 'Server'
            };
        }

        getVisibleFiberNodes().forEach(function(node) {
            visibleNodes[buildNodeKey(node.type, node.id)] = {
                latlng: L.latLng(Number(node.latitude), Number(node.longitude)),
                label: node.name || node.type
            };
        });

        getVisibleOnts().forEach(function(ont) {
            visibleNodes[buildNodeKey('ONT', ont.serviceId)] = {
                latlng: L.latLng(Number(ont.latitude), Number(ont.longitude)),
                label: ont.customerName || 'ONT'
            };
        });

        return visibleNodes;
    }

    function buildNodeIndex() {
        const index = new Map();

        (state.data.servers || []).forEach(function(item) {
            if (item.hasCoordinates) {
                index.set(buildNodeKey('SERVER', item.id), item);
            }
        });

        (state.data.fiberNodes || []).forEach(function(item) {
            index.set(buildNodeKey(item.type, item.id), item);
        });

        (state.data.onts || []).forEach(function(item) {
            index.set(buildNodeKey('ONT', item.serviceId), item);
        });

        return index;
    }

    function getSelectedServer() {
        return (state.data.servers || []).find(function(item) {
            return String(item.id) === String(state.selectedServerId);
        }) || null;
    }

    function getVisibleFiberNodes() {
        const items = state.data.fiberNodes || [];
        if (state.selectedNodeKey === ALL_OPTION) {
            return items;
        }
        return items.filter(function(item) {
            return buildNodeKey(item.type, item.id) === state.selectedNodeKey;
        });
    }

    function getVisibleOnts() {
        const items = state.data.onts || [];
        if (state.selectedOntServiceId === ALL_OPTION) {
            return items;
        }
        return items.filter(function(item) {
            return String(item.serviceId) === String(state.selectedOntServiceId);
        });
    }

    function fitMap(latlngs) {
        if (!state.map) {
            return;
        }

        if (!latlngs || latlngs.length === 0) {
            state.map.setView(DEFAULT_CENTER, DEFAULT_ZOOM);
            return;
        }

        if (latlngs.length === 1) {
            state.map.setView(latlngs[0], 15);
            return;
        }

        const bounds = L.latLngBounds(latlngs);
        state.map.fitBounds(bounds.pad(0.2), {
            maxZoom: 16
        });
    }

    function clearLayer(layer) {
        if (layer && typeof layer.clearLayers === 'function') {
            layer.clearLayers();
        }
    }

    function showEmptyState(message) {
        state.dom.emptyState.classList.add('show');
        state.dom.emptyState.innerHTML = '<div>'
            + '<i class="fas fa-map-location-dot" style="font-size: 54px; margin-bottom: 14px; opacity: 0.5;"></i>'
            + '<p style="margin: 0; font-size: 16px; color: #f8fafc;">Peta belum siap.</p>'
            + '<p style="margin: 10px 0 0; font-size: 13px;">' + escapeHtml(message || 'Data koordinat belum tersedia.') + '</p>'
            + '</div>';
    }

    function hideEmptyState() {
        state.dom.emptyState.classList.remove('show');
    }

    function setStatus(message, type) {
        const palette = {
            success: '#86efac',
            error: '#fca5a5',
            warning: '#fdba74',
            info: '#93c5fd'
        };
        state.dom.statusText.textContent = message;
        state.dom.statusText.style.color = palette[type] || '#f8fafc';
    }

    function buildFiberPopup(node) {
        const infoLines = [];
        if (node.code) {
            infoLines.push('Kode: ' + node.code);
        }
        if (node.serverName) {
            infoLines.push('Server: ' + node.serverName);
        }
        if (node.parentName) {
            infoLines.push('Parent: ' + node.parentName);
        }
        if (node.splitter) {
            infoLines.push('Splitter: ' + node.splitter);
        }
        if (node.location) {
            infoLines.push('Lokasi: ' + node.location);
        }

        return '<div class="mapping-popup"><strong>' + escapeHtml(node.type + ' - ' + (node.name || '-')) + '</strong>'
            + infoLines.map(function(line) {
                return '<div>' + escapeHtml(line) + '</div>';
            }).join('')
            + '</div>';
    }

    function buildOntPopup(ont) {
        return '<div class="mapping-popup"><strong>' + escapeHtml(ont.customerName || 'ONT') + '</strong>'
            + '<div>' + escapeHtml(ont.customerCode || '-') + '</div>'
            + '<div style="margin-top: 6px;">' + escapeHtml(ont.installationAddress || '-') + '</div>'
            + '</div>';
    }

    function mapStatusClass(value) {
        const normalized = String(value || '').toLowerCase();
        if (normalized === 'active') {
            return 'active';
        }
        if (normalized === 'suspended') {
            return 'suspended';
        }
        return 'pending';
    }

    function detailItem(label, value) {
        return '<div class="mapping-detail-item">'
            + '<div class="mapping-detail-item-label">' + escapeHtml(label) + '</div>'
            + '<div class="mapping-detail-item-value">' + escapeHtml(value || '-') + '</div>'
            + '</div>';
    }

    function compareByName(left, right) {
        const a = (left.name || left.customerName || '').toLowerCase();
        const b = (right.name || right.customerName || '').toLowerCase();
        return a.localeCompare(b);
    }

    function buildNodeOptionLabel(item) {
        const parts = [];
        if (item.code) {
            parts.push(item.code);
        }
        parts.push(item.name || '-');
        return parts.join(' - ');
    }

    function buildOntOptionLabel(item) {
        const parts = [item.customerName || '-', item.customerCode || '-'];
        if (item.odpName) {
            parts.push(item.odpName);
        }
        return parts.join(' - ');
    }

    function buildNodeKey(type, id) {
        return String(type) + ':' + String(id);
    }

    async function fetchJson(url, options) {
        const requestOptions = Object.assign({
            headers: {
                Accept: 'application/json'
            }
        }, options || {});

        requestOptions.headers = requestOptions.headers || {};
        requestOptions.headers.Accept = 'application/json';

        if (requestOptions.body && !requestOptions.headers['Content-Type']) {
            requestOptions.headers['Content-Type'] = 'application/json';
        }

        const method = String(requestOptions.method || 'GET').toUpperCase();
        if (method !== 'GET' && method !== 'HEAD') {
            requestOptions.headers[window.csrfHeader || 'X-CSRF-TOKEN'] = window.csrfToken || '';
        }

        const response = await fetch(url, requestOptions);
        const result = await response.json();
        if (!response.ok || !result.success) {
            throw new Error((result && result.message) || 'Permintaan gagal diproses.');
        }
        return result.data;
    }

    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function escapeAttribute(value) {
        return escapeHtml(value).replace(/`/g, '&#96;');
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
