/*  Copyright (C) 2015-2026 115ek, akasaka / Genjitsu Labs, Alicia Hormann,
    Andreas Böhler, Andreas Shimokawa, Andrew Watkins, angelpup, Carsten Pfeiffer,
    Cre3per, Damien Gaignon, DanialHanif, Daniel Dakhno, Daniele Gobbetti, Daniel
    Thompson, Da Pa, Dmytro Bielik, Frank Ertl, Gabriele Monaco, GeekosaurusR3x,
    Guido Jäkel, Gordon Williams, Jean-François Greffier, jfgreffier, jhey, João Paulo
    Barraca, Jochen S, Johannes Krude, José Rebelo, ksiwczynski, ladbsoft,
    Lesur Frederic, Maciej Kuśnierz, mamucho, Manuel Ruß, Maxime Reyrolle,
    maxirnilian, Michael, narektor, Noodlez, odavo32nof, opavlov, pangwalla,
    Pavel Elagin, Petr Kadlec, Petr Vaněk, protomors, Quallenauge, Quang Ngô,
    Raghd Hamzeh, Sami Alaoui, Sebastian Kranz, sedy89, Sophanimus, Stefan Bora,
    Taavi Eomäe, thermatk, tiparega, Vadim Kaushan, x29a, xaos, Yoran Vulker,
    Yukai Li, Thomas Kuehne

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.model;

import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.UnknownDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.aawireless.AAWirelessCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.asteroidos.AsteroidOSDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.atcbleoepl.ATCBLEOEPLCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.bandwpseries.BandWPSeriesDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.banglejs.BangleJSCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.beurer.BeurerBm59Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.beurer.BeurerBm69Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.binary_sensor.coordinator.BinarySensorCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.bm2.Bm2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.bm6.Bm6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.braun.BraunBPW4500DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.cardo.Ls24xDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.casio.ecbs100.CasioECBS100DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.casio.gb6900.CasioGB6900DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.casio.gbd200.CasioGBD200DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.casio.gbx100.CasioGBX100DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.casio.gwb5600.CasioGMWB5000DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.casio.gwb5600.CasioGWB5600DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.casio.ggb100.CasioGGB100DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.cmfwatchpro.CmfWatchPro2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.cmfwatchpro.CmfWatchPro3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.cmfwatchpro.CmfWatchProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.coospo.CoospoH6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.coospo.CoospoH808SCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.coospo.CoospoH9ZCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.coospo.CoospoHW807Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.coospo.CoospoHW9Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.cycling_sensor.coordinator.CyclingSensorCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.divoom.PixooCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.dji.cameras.DjiOsmoAction4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.concept2.Concept2PM5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.coros.CorosPace3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.domyos.DomyosT540Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.earfun.EarFunAirPro4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.earfun.EarFunAirSCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.earfun.EarFunFreePro3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.eightbitdo.EightBitDoMicroCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.evenrealities.G1DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.femometer.FemometerVinca2DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.fitcloud.ak102.Ak102Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.fitcloud.ak75.Ak75ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.fitcloud.ak86.Ak86Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.fitcloud.colmi.ColmiI31Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.fitpro.FitProDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.fitpro.colacao.ColaCao21Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.fitpro.colacao.ColaCao23Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.flipper.zero.FlipperZeroCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.galaxy_buds.GalaxyBuds2DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.galaxy_buds.GalaxyBuds2ProDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.galaxy_buds.GalaxyBuds3ProDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.galaxy_buds.GalaxyBudsDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.galaxy_buds.GalaxyBudsLiveDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.galaxy_buds.GalaxyBudsProDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge1000Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge1030Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge1030PlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge1040Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge1050Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge130Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge130PlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge25Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge510Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge520Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge520PlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge530Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge540Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge550Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge810Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge820Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge830Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge840Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdge850Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdgeExplore1000Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdgeExplore2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdgeExplore820Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdgeExploreCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.bike.GarminEdgeMtbCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.gps.GarminETrexSeCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.gps.GarminGpsmap66iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.gps.GarminGpsmap66sCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.gps.GarminGpsmap66srCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.gps.GarminGpsmapH1Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.gps.GarminInReachMini2;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.hrm.GarminHrm200Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.hrm.GarminHrm600Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.hrm.GarminHrmProPlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.cirqa.GarminCirqaCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.d2.GarminD2AirCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.d2.GarminD2AirX10Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.d2.GarminD2AirX15Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.d2.GarminD2Mach1Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.d2.GarminD2Mach1ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.d2.GarminD2Mach2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.d2.GarminD2Mach2ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.descent.GarminDescentG1Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.descent.GarminDescentG2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.descent.GarminDescentMk3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.descent.GarminDescentMk3iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.enduro.GarminEnduro2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.enduro.GarminEnduro3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.enduro.GarminEnduro4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.enduro.GarminEnduroCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.epix.GarminEpixCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.epix.GarminEpixGen2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.epix.GarminEpixProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix3ChronosCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix3HrCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix5PlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix5SCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix5SPlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix5XCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix5XPlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix6ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix6SCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix6SProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix6SSportCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix6SportCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix6SProSolarCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix6SSapphireCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix6SapphireCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix6XCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix6XProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix6XProSolarCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix6XSapphireCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix7Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix7ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix7ProSolarCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix7SCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix7SProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix7SProSolarCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix7XCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix7XProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix7XProSolarCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix8Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix8ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix8SolarCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix9Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix9ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix9ProSolarCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenixECoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner165Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner165MusicCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner170Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner170MusicCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner220Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner225Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner230Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner235Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner245Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner245MusicCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner255Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner255MusicCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner255SCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner255SMusicCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner25Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner265Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner265SCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner30Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner35Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner45Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner45PlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner55Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner570Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner620Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner630Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner645Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner645MusicCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner70Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner735XTCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner745Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner920Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner920XTCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner935Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner945Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner945LTECoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner955Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner965Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.forerunner.GarminForerunner970Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinct2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinct2SCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinct2SSolarCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinct2SSurfCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinct2SolTacCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinct2SolarCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinct2XCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinct2XSolarCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinct2XTacticalCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinct3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinct3SolarCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinct3SolarTacticalCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinct3TacticalCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinctCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinctCrossoverCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinctECoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinctSolarCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinctSolarTacticalCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.instinct.GarminInstinctTacticalCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.lily.GarminLily2ActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.lily.GarminLily2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.lily.GarminLilyCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.quatix.GarminQuatix6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.quatix.GarminQuatix6XCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.quatix.GarminQuatix7Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.quatix.GarminQuatix7XCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.quatix.GarminQuatix8Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.swim.GarminSwim2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.tactix.GarminTactix7Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.tactix.GarminTactix8Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.tactix.GarminTactix8SolarCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenu2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenu2PlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenu2SCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenu3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenu3SCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenu4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenuCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenuSq2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenuSq2MusicCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenuSqCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenuSqMusicCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenuX1Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivoactive.GarminVivoActive3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivoactive.GarminVivoActive3MusicCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivoactive.GarminVivoActive3TCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivoactive.GarminVivoActive4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivoactive.GarminVivoActive4SCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivoactive.GarminVivoActive5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivoactive.GarminVivoActive6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivoactive.GarminVivoActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivoactive.GarminVivoActiveHrCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivomove.GarminVivomoveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivomove.GarminVivomoveHrCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivomove.GarminVivomoveSportCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivomove.GarminVivomoveStyleCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivomove.GarminVivomoveTrendCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivosmart.GarminVivosmart3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivosmart.GarminVivosmart4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivosmart.GarminVivosmart5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivosmart.GarminVivosmartCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivosmart.GarminVivosmartHrCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivosmart.GarminVivosmartHrPlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.vivosport.GarminVivosportCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.generic_headphones.GenericHeadphonesCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.generic_hr.GenericHeartRateCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.generic_scale.GenericWeightScaleCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.BlackviewR3MaxCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.ColmiP10Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.D3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.DotnP66DCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.GrvFc1Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.HaylouWatch2ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.HiFutureZone2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.ImikiFrame2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.KieslectCallingSmartwatchKrProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.OukitelBT103Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.QecnatoS10Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.R1Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.S52Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.SwissPeakCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.T70Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.Y66Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.watches.Y6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gloryfitpro.watches.DM58Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.gree.GreeAcCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.hama.fit6900.HamaFit6900DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweibanda2.HuaweiBandA2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatch6.HuaweiWatch6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatch6.HuaweiWatch6ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchgt7.HuaweiWatchGT7Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchgt7.HuaweiWatchGT7ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.C20ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.ColmiP81Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.FireBoltt069Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.FireBolttNinjaPhantomCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.GarettEvaCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.haylou.HaylouS35AncCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.hplus.EXRIZUK8Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.hplus.HPlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.hplus.MakibesF68Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.hplus.Q8Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.hplus.SG2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitband5.AmazfitBand5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitbip.AmazfitBipCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitbip.AmazfitBipLiteCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitbip3.AmazfitBip3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitbip3pro.AmazfitBip3ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitbips.AmazfitBipSCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitbips.AmazfitBipSLiteCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitbipu.AmazfitBipUCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitbipupro.AmazfitBipUProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitcor.AmazfitCorCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitcor2.AmazfitCor2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitgtr.AmazfitGTRCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitgtr.AmazfitGTRLiteCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitgtr2.AmazfitGTR2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitgtr2.AmazfitGTR2eCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitgts.AmazfitGTSCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitgts2.AmazfitGTS2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitgts2.AmazfitGTS2MiniCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitgts2.AmazfitGTS2eCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitneo.AmazfitNeoCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitpop.AmazfitPopCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitpoppro.AmazfitPopProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfittrex.AmazfitTRexCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfittrexpro.AmazfitTRexProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitvergel.AmazfitVergeLCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.amazfitx.AmazfitXCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.miband2.MiBand2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.miband2.MiBand2HRXCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.miband3.MiBand3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.miband4.MiBand4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.miband5.MiBand5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.miband6.MiBand6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppe.ZeppECoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.rings.AmazfitHelioRingCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.straps.AmazfitHelioStrapCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitActive2NfcCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitActive2RoundCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitActive2SquareCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitActive3PremiumCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitActiveEdgeCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitActiveMaxCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitBalance2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitBalance2XTCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitBalance3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitBalanceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitBalanceUltraCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitBand7Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitBip5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitBip5UnityCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitBip6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitBipMaxCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitCheetah2ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitCheetah2UltraCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitCheetahProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitCheetahRoundCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitCheetahSquareCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitFalconCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitGTR3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitGTR3ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitGTR4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitGTRMiniCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitGTS3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitGTS4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitGTS4MiniCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitGTS4MiniNewCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitTRex2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitTRex3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitTRex3Pro44mmCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitTRex3Pro48mmCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitTRexUltra2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.AmazfitTRexUltraCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.watches.MiBand7Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.freearc.HuaweiFreearcCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.freebuds5i.HuaweiFreebuds5iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.freebuds6.HuaweiFreebuds6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.freebudspro.HuaweiFreebudsProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.freeclip.HuaweiFreeClip2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honorband10.HonorBand10Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honorband3.HonorBand3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honorband4.HonorBand4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honorband5.HonorBand5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honorband6.HonorBand6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honorband7.HonorBand7Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honorband9.HonorBand9Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honormagicwatch.HonorMagicWatchCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honormagicwatch2.HonorMagicWatch2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honorwatch4.HonorWatch4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honorwatch5.HonorWatch5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honorwatch5pro.HonorWatch5ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honorwatch6.HonorWatch6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honorwatchgs3.HonorWatchGS3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.honorwatchgspro.HonorWatchGSProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiband10.HuaweiBand10Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiband11.HuaweiBand11Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiband11pro.HuaweiBand11ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiband2pro.HuaweiBand2ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiband3pro.HuaweiBand3ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiband4pro.HuaweiBand4ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiband6.HuaweiBand6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiband7.HuaweiBand7Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiband8.HuaweiBand8Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiband9.HuaweiBand9Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweibandaw70.HuaweiBandAw70Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweitalkbandb6.HuaweiTalkBandB6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatch3.HuaweiWatch3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatch4pro.HuaweiWatch4ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatch5.HuaweiWatch5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchd2.HuaweiWatchD2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchd3.HuaweiWatchD3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchfit.HuaweiWatchFitCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchfit2.HuaweiWatchFit2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchfit3.HuaweiWatchFit3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchfit4pro.HuaweiWatchFit4ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchfit5.HuaweiWatchFit5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchfit5pro.HuaweiWatchFit5ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchgt.HuaweiWatchGTCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchgt2.HuaweiWatchGT2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchgt2e.HuaweiWatchGT2eCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchgt3.HuaweiWatchGT3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchgt4.HuaweiWatchGT4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchgt5.HuaweiWatchGT5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchgt6.HuaweiWatchGT6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchgtcyber.HuaweiWatchGTCyberCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchgtrunner.HuaweiWatchGTRunnerCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchgtrunner2.HuaweiWatchGTRunner2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchultimate.HuaweiWatchUltimateCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweiwatchultimate2.HuaweiWatchUltimate2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.id115.ID115Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.idasen.IdasenCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.igpsport.IGPSportBSC100MaxCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.igpsport.IGPSportBSC200Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.igpsport.IGPSportBSC200SCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.igpsport.IGPSportBSC300Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.igpsport.IGPSportBSC500Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.igpsport.IGPSportBiNaviAirCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.igpsport.IGPSportBiNaviCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.igpsport.IGPSportiGS630Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.igpsport.IGPSportiGS630SCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.igpsport.IGPSportiGS800Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.innovo.InnovoPulseOximeterCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.itag.ITagCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.jabra.JabraEvolve255Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.jabra.JabraEvolve265Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.jabra.JabraEvolve65Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.jyou.BFH16DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.jyou.TeclastH30.TeclastH30Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.jyou.y5.Y5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.keephealth.C60DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.laxasfit.LaxasFitCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.lefun.BohemicSmartBraceletDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.lefun.LefunDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.lefun.VivitarHrBpMonitorActivityTrackerCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.lenovo.watch9.Watch9DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.lenovo.watchxplus.WatchXPlusDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.liveview.LiveviewCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.makibeshr3.MakibesHR3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.marstek.MarstekB2500DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.mijia_lywsd.MijiaLywsd02Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.mijia_lywsd.MijiaLywsd03Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.mijia_lywsd.MijiaMhoC303Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.mijia_lywsd.MijiaXmwsdj04Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.miscale.MiCompositionScaleCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.miscale.MiSmartScaleCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.ninebot.NinebotScooterCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.KW19BCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.onebyone.OneByoneScaleCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moondrop.MoondropSpaceTravel2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moondrop.MoondropSpaceTravel2UltraCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moondrop.MoondropSpaceTravelCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.AdvanWatchSE1AICoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.AltVibeCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.BettdowFB051Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.BlackviewR60Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.C20Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.ColmiI28UltraCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.ColmiP76Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.ColmiP80Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.ColmiV72Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.ColmiV73Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.ColmiV76Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.ColmiV89Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.FireBolttTalkCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.HC96Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.ITechActive3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.KT80Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.KsixVentureCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.L70Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.MT55Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.MisirunC17Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.P22B1Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.R50ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.RainbuvvyT97Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.ViranC29Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.moyoung.ZL02DCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.no1f1.No1F1Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.nothing.CmfBuds2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.nothing.CmfBuds2PlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.nothing.CmfBuds2aCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.nothing.CmfBudsPro2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.nothing.Ear1Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.nothing.Ear2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.nothing.EarACoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.nothing.EarStickCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.nut.NutCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.nxwear.NxWearB8Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.ollee.OlleeDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.onemoresonoflow.OneMoreSonoFlowCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.onemoresonoflow.OneMoreSonoFlowSECoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.onetouch.OneTouchCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.oppo.OppoEncoAir2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.oppo.OppoEncoAirCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.oppo.OppoEncoBuds2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.overmax.OVTouch26Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.pebble.PebbleCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.pinetime.PineTimeJFCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.pixel.PixelBudsACoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.polar.PolarH10DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.polar.PolarH9DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.bose.NC700Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.bose.QC35Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.qhybrid.QHybridCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.qnscale.QnScaleCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.realme.RealmeBudsAir5ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.realme.RealmeBudsAir6ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.realme.RealmeBudsT100Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.realme.RealmeBudsT110Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.realme.RealmeBudsT200Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.realme.RealmeBudsT300Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.RedmiBuds3ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.RedmiBuds4ActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.RedmiBuds5ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.RedmiBuds6ActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.RedmiBuds6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.RedmiBuds6LiteCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.RedmiBuds6ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.RedmiBuds8ActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.roidmi.Roidmi1Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.roidmi.Roidmi3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.roidmi.RoidmiF8Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sbm_67.SanitasSBM67Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sbm_67.SilverCrestSBM67Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.scannable.ScannableDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sennheiser.SennheiserMomentumInEarWirelessCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.shokz.ShokzOpenRunPro2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.shokz.ShokzOpenSwimProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sinilink.SinilinkCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.smaq2oss.SMAQ2OSSCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soflow.SoFlowCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyLinkBudsCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyLinkBudsSCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWF1000XM3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWF1000XM4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWF1000XM5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWFC500Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWFC510Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWFC700NCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWFC710NCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWFSP800NCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWH1000XM2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWH1000XM3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWH1000XM4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWH1000XM5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWH1000XM6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWHCH720NCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWHULT900NCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWIC100Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.coordinators.SonyWISP600NCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.reonpocket.SonyReonPocketProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.wena3.SonyWena3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sonyswr12.SonySWR12DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundbrenner.SoundbrennerCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreP42iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreP41iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreP40iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreP31iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreP3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreA1Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreP2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreP2iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreP2MiniCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreA2NCCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreA3iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreP3iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreK20iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreP30iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreP20iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreA30iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreAeroFit2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreLiberty3ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreLiberty4NCCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreLifeTuneCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreLifeTuneXRCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreMotion300Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreMotionPlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreQ30Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreR50iNCCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreSpaceOneCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreSportX20Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreV20iCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.supercars.SuperCarsCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.teufel.TeufelMyndCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.thermalprinter.ThermalPrinterCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.tlw64.TLW64Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.ultrahuman.UltrahumanDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.ugreen.hitunemax5c.UgreenHituneMax5cCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.um25.Coordinator.UM25Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.una.UnaDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.vesc.VescCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.viatom.F8ScaleCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.vibratissimo.VibratissimoCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.victron.VictronSmartShuntCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.waspos.WaspOSCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.wearfit.WearFitCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.withingssteelhr.WithingsSteelHRDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand10Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand10ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand11ActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand11Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand4CCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand7ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand8ActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand8Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand8ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand9ActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand9Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand9ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiWatchColorSportCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiWatchLiteCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiSmartBand2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiSmartBand3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiSmartBandProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch2LiteCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch3ActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch5ActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch5LiteCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch6Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatchMoveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.XiaomiWatch5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.XiaomiWatchS1ActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.XiaomiWatchS1Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.XiaomiWatchS1ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.XiaomiWatchS3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.XiaomiWatchS4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.XiaomiWatchS5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi_hipee.XiaomiHipeeP1Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi_scooters.XiaomiScooter5MaxCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.zeblaze.Beyond3ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.zeblaze.stratos4pro.Stratos4ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xplora.XploraXmoveDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xwatch.XWatchCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.ColmiR02Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.ColmiR03Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.ColmiR06Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.ColmiR07Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.ColmiR09Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.ColmiR10Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.ColmiR12Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.H59Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.YawellR05Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.YawellR10Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.YawellR11Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.YawellY27Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.zendure.SolarFlowDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.zetime.ZeTimeCoordinator;
import nodomain.freeyourgadget.gadgetbridge.service.devices.gatt_client.BleGattClientCoordinator;

/**
 * For every supported device, a device type constant must exist.
 * <p>
 * Note: they name of the enum is stored in the DB, so it is fixed forever,
 * and may not be changed.
 * <p>
 * Migration note: As of <a href="https://codeberg.org/Freeyourgadget/Gadgetbridge/pulls/3347">#3347</a>,
 * the numeric device id is not used anymore. If your database has development devices that still used
 * the numeric ID, you need to update assets/migrations/devicetype.json before installing Gadgetbridge
 * after rebasing, in order for your device to be migrated correctly. If you failed to do this and the
 * device is now not being displayed, please update the file and uncomment the call to migrateDeviceTypes
 * in GBApplication.
 */
public enum DeviceType {
    UNKNOWN(UnknownDeviceCoordinator::new),
    PEBBLE(PebbleCoordinator::new),
    MIBAND(MiBandCoordinator::new),
    MIBAND2(MiBand2Coordinator::new),
    MIBAND2_HRX(MiBand2HRXCoordinator::new),
    AMAZFITBIP(AmazfitBipCoordinator::new),
    AMAZFITCOR(AmazfitCorCoordinator::new),
    MIBAND3(MiBand3Coordinator::new),
    AMAZFITCOR2(AmazfitCor2Coordinator::new),
    MIBAND4(MiBand4Coordinator::new),
    MIBAND4C(MiBand4CCoordinator::new),
    AMAZFITBIP_LITE(AmazfitBipLiteCoordinator::new),
    AMAZFITGTR(AmazfitGTRCoordinator::new),
    AMAZFITGTS(AmazfitGTSCoordinator::new),
    AMAZFITBIPS(AmazfitBipSCoordinator::new),
    AMAZFITGTR_LITE(AmazfitGTRLiteCoordinator::new),
    AMAZFITTREX(AmazfitTRexCoordinator::new),
    MIBAND5(MiBand5Coordinator::new),
    AMAZFITBAND5(AmazfitBand5Coordinator::new),
    AMAZFITBIPS_LITE(AmazfitBipSLiteCoordinator::new),
    AMAZFITGTR2(AmazfitGTR2Coordinator::new),
    AMAZFITGTS2(AmazfitGTS2Coordinator::new),
    AMAZFITBIPU(AmazfitBipUCoordinator::new),
    AMAZFITVERGEL(AmazfitVergeLCoordinator::new),
    AMAZFITBIPUPRO(AmazfitBipUProCoordinator::new),
    AMAZFITNEO(AmazfitNeoCoordinator::new),
    AMAZFITGTS2_MINI(AmazfitGTS2MiniCoordinator::new),
    ZEPP_E(ZeppECoordinator::new),
    AMAZFITGTR2E(AmazfitGTR2eCoordinator::new),
    AMAZFITGTS2E(AmazfitGTS2eCoordinator::new),
    AMAZFITX(AmazfitXCoordinator::new),
    MIBAND6(MiBand6Coordinator::new),
    AMAZFITTREXPRO(AmazfitTRexProCoordinator::new),
    AMAZFITPOP(AmazfitPopCoordinator::new),
    AMAZFITPOPPRO(AmazfitPopProCoordinator::new),
    MIBAND7(MiBand7Coordinator::new),
    MIBAND7PRO(MiBand7ProCoordinator::new),
    MIBAND8(MiBand8Coordinator::new),
    MIBAND8ACTIVE(MiBand8ActiveCoordinator::new),
    MIBAND8PRO(MiBand8ProCoordinator::new),
    MIBAND9(MiBand9Coordinator::new),
    MIBAND9ACTIVE(MiBand9ActiveCoordinator::new),
    MIBAND9PRO(MiBand9ProCoordinator::new),
    MIBAND10(MiBand10Coordinator::new),
    MIBAND10PRO(MiBand10ProCoordinator::new),
    MIBAND11(MiBand11Coordinator::new),
    MIBAND11ACTIVE(MiBand11ActiveCoordinator::new),
    MIWATCHLITE(MiWatchLiteCoordinator::new),
    MIWATCHCOLORSPORT(MiWatchColorSportCoordinator::new),
    REDMIBUDS3PRO(RedmiBuds3ProCoordinator::new),
    REDMIBUDS4ACTIVE(RedmiBuds4ActiveCoordinator::new),
    REDMIBUDS5PRO(RedmiBuds5ProCoordinator::new),
    REDMIBUDS6(RedmiBuds6Coordinator::new),
    REDMIBUDS6ACTIVE(RedmiBuds6ActiveCoordinator::new),
    REDMIBUDS6LITE(RedmiBuds6LiteCoordinator::new),
    REDMIBUDS6PRO(RedmiBuds6ProCoordinator::new),
    REDMIBUDS8ACTIVE(RedmiBuds8ActiveCoordinator::new),
    REDMIWATCH3ACTIVE(RedmiWatch3ActiveCoordinator::new),
    REDMIWATCH3(RedmiWatch3Coordinator::new),
    REDMISMARTBAND2(RedmiSmartBand2Coordinator::new),
    REDMISMARTBAND3(RedmiSmartBand3Coordinator::new),
    REDMIWATCH2(RedmiWatch2Coordinator::new),
    REDMIWATCH2LITE(RedmiWatch2LiteCoordinator::new),
    REDMISMARTBANDPRO(RedmiSmartBandProCoordinator::new),
    REDMIWATCH4(RedmiWatch4Coordinator::new),
    REDMIWATCH5(RedmiWatch5Coordinator::new),
    REDMIWATCH5ACTIVE(RedmiWatch5ActiveCoordinator::new),
    REDMIWATCH5LITE(RedmiWatch5LiteCoordinator::new),
    REDMIWATCH6(RedmiWatch6Coordinator::new),
    REDMIWATCHMOVE(RedmiWatchMoveCoordinator::new),
    XIAOMI_WATCH_S1_ACTIVE(XiaomiWatchS1ActiveCoordinator::new),
    XIAOMI_WATCH_S1_PRO(XiaomiWatchS1ProCoordinator::new),
    XIAOMI_WATCH_S1(XiaomiWatchS1Coordinator::new),
    XIAOMI_WATCH_S3(XiaomiWatchS3Coordinator::new),
    XIAOMI_WATCH_S4(XiaomiWatchS4Coordinator::new),
    XIAOMI_WATCH_S5(XiaomiWatchS5Coordinator::new),
    XIAOMI_WATCH_5(XiaomiWatch5Coordinator::new),
    AMAZFITGTS3(AmazfitGTS3Coordinator::new),
    AMAZFITGTR3(AmazfitGTR3Coordinator::new),
    AMAZFITGTR4(AmazfitGTR4Coordinator::new),
    AMAZFITBAND7(AmazfitBand7Coordinator::new),
    AMAZFITGTS4(AmazfitGTS4Coordinator::new),
    AMAZFITGTS4MINI(AmazfitGTS4MiniCoordinator::new),
    AMAZFITGTS4MININEW(AmazfitGTS4MiniNewCoordinator::new),
    AMAZFITTREX2(AmazfitTRex2Coordinator::new),
    AMAZFITTREX3(AmazfitTRex3Coordinator::new),
    AMAZFITTREX3PRO44MM(AmazfitTRex3Pro44mmCoordinator::new),
    AMAZFITTREX3PRO48MM(AmazfitTRex3Pro48mmCoordinator::new),
    AMAZFITGTR3PRO(AmazfitGTR3ProCoordinator::new),
    AMAZFITBIP3(AmazfitBip3Coordinator::new),
    AMAZFITBIP3PRO(AmazfitBip3ProCoordinator::new),
    AMAZFITCHEETAHPRO(AmazfitCheetahProCoordinator::new),
    AMAZFITCHEETAH2PRO(AmazfitCheetah2ProCoordinator::new),
    AMAZFITCHEETAH2ULTRA(AmazfitCheetah2UltraCoordinator::new),
    AMAZFITCHEETAHSQUARE(AmazfitCheetahSquareCoordinator::new),
    AMAZFITCHEETAHROUND(AmazfitCheetahRoundCoordinator::new),
    AMAZFITBIP5(AmazfitBip5Coordinator::new),
    AMAZFITBIP5UNITY(AmazfitBip5UnityCoordinator::new),
    AMAZFITBIP6(AmazfitBip6Coordinator::new),
    AMAZFITBIPMAX(AmazfitBipMaxCoordinator::new),
    AMAZFITTREXULTRA(AmazfitTRexUltraCoordinator::new),
    AMAZFITTREXULTRA2(AmazfitTRexUltra2Coordinator::new),
    AMAZFITGTRMINI(AmazfitGTRMiniCoordinator::new),
    AMAZFITFALCON(AmazfitFalconCoordinator::new),
    AMAZFITBALANCE(AmazfitBalanceCoordinator::new),
    AMAZFITBALANCE2(AmazfitBalance2Coordinator::new),
    AMAZFITBALANCE2XT(AmazfitBalance2XTCoordinator::new),
    AMAZFITBALANCE3(AmazfitBalance3Coordinator::new),
    AMAZFITBALANCEULTRA(AmazfitBalanceUltraCoordinator::new),
    AMAZFITACTIVE(AmazfitActiveCoordinator::new),
    AMAZFITACTIVE2(AmazfitActive2RoundCoordinator::new),
    AMAZFITACTIVE2NFC(AmazfitActive2NfcCoordinator::new),
    AMAZFITACTIVE2SQUARE(AmazfitActive2SquareCoordinator::new),
    AMAZFITACTIVE3PREMIUM(AmazfitActive3PremiumCoordinator::new),
    AMAZFITACTIVEEDGE(AmazfitActiveEdgeCoordinator::new),
    AMAZFITACTIVEMAX(AmazfitActiveMaxCoordinator::new),
    AMAZFITHELIORING(AmazfitHelioRingCoordinator::new),
    AMAZFITHELIOSTRAP(AmazfitHelioStrapCoordinator::new),
    HPLUS(HPlusCoordinator::new),
    MAKIBESF68(MakibesF68Coordinator::new),
    EXRIZUK8(EXRIZUK8Coordinator::new),
    Q8(Q8Coordinator::new),
    SG2(SG2Coordinator::new),
    NO1F1(No1F1Coordinator::new),
    P22B1(P22B1Coordinator::new),
    TECLASTH30(TeclastH30Coordinator::new),
    Y5(Y5Coordinator::new),
    XWATCH(XWatchCoordinator::new),
    ZETIME(ZeTimeCoordinator::new),
    ID115(ID115Coordinator::new),
    WATCH9(Watch9DeviceCoordinator::new),
    WATCHXPLUS(WatchXPlusDeviceCoordinator::new),
    ROIDMI(Roidmi1Coordinator::new),
    ROIDMI3(Roidmi3Coordinator::new),
    ROIDMI_F8(RoidmiF8Coordinator::new),
    CASIOECBS100(CasioECBS100DeviceCoordinator::new),

    CASIOGB6900(CasioGB6900DeviceCoordinator::new),
    CASIOGBX100(CasioGBX100DeviceCoordinator::new),
    CASIOGBD200(CasioGBD200DeviceCoordinator::new),
    CASIOGWB5600(CasioGWB5600DeviceCoordinator::new),
    CASIOGMWB5000(CasioGMWB5000DeviceCoordinator::new),
    CASIOGGB100(CasioGGB100DeviceCoordinator::new),
    MISMARTSCALE(MiSmartScaleCoordinator::new),
    MICOMPOSITIONSCALE(MiCompositionScaleCoordinator::new),
    ONEBYONE_SCALE(OneByoneScaleCoordinator::new),
    BFH16(BFH16DeviceCoordinator::new),
    MAKIBESHR3(MakibesHR3Coordinator::new),
    OVTOUCH26(OVTouch26Coordinator::new),
    BANGLEJS(BangleJSCoordinator::new),
    FOSSILQHYBRID(QHybridCoordinator::new),
    TLW64(TLW64Coordinator::new),
    PINETIME_JF(PineTimeJFCoordinator::new),
    POLARH9(PolarH9DeviceCoordinator::new),
    POLARH10(PolarH10DeviceCoordinator::new),
    MIJIA_LYWSD02(MijiaLywsd02Coordinator::new),
    MIJIA_LYWSD03(MijiaLywsd03Coordinator::new),
    MIJIA_XMWSDJ04(MijiaXmwsdj04Coordinator::new),
    MIJIA_MHO_C303(MijiaMhoC303Coordinator::new),
    LEFUN(LefunDeviceCoordinator::new),
    VIVITAR_HR_BP_MONITOR_ACTIVITY_TRACKER(VivitarHrBpMonitorActivityTrackerCoordinator::new),
    BOHEMIC_SMART_BRACELET(BohemicSmartBraceletDeviceCoordinator::new),
    SMAQ2OSS(SMAQ2OSSCoordinator::new),
    FITPRO(FitProDeviceCoordinator::new),
    COLACAO21(ColaCao21Coordinator::new),
    COLACAO23(ColaCao23Coordinator::new),
    AK102(Ak102Coordinator::new),
    AK75_PRO(Ak75ProCoordinator::new),
    AK86(Ak86Coordinator::new),
    LAXASFIT_Q11(LaxasFitCoordinator::new),
    ITAG(ITagCoordinator::new),
    IKEA_IDASEN(IdasenCoordinator::new),
    NUTMINI(NutCoordinator::new),
    NX_WEAR_B8(NxWearB8Coordinator::new),
    VIVOMOVE_HR(GarminVivomoveHrCoordinator::new),
    GARMIN_HRM_PRO_PLUS(GarminHrmProPlusCoordinator::new),
    GARMIN_HRM_200(GarminHrm200Coordinator::new),
    GARMIN_HRM_600(GarminHrm600Coordinator::new),
    GARMIN_EDGE_25(GarminEdge25Coordinator::new),
    GARMIN_EDGE_130(GarminEdge130Coordinator::new),
    GARMIN_EDGE_130_PLUS(GarminEdge130PlusCoordinator::new),
    GARMIN_EDGE_510(GarminEdge510Coordinator::new),
    GARMIN_EDGE_520(GarminEdge520Coordinator::new),
    GARMIN_EDGE_520_PLUS(GarminEdge520PlusCoordinator::new),
    GARMIN_EDGE_530(GarminEdge530Coordinator::new),
    GARMIN_EDGE_540(GarminEdge540Coordinator::new),
    GARMIN_EDGE_550(GarminEdge550Coordinator::new),
    GARMIN_EDGE_810(GarminEdge810Coordinator::new),
    GARMIN_EDGE_820(GarminEdge820Coordinator::new),
    GARMIN_EDGE_830(GarminEdge830Coordinator::new),
    GARMIN_EDGE_840(GarminEdge840Coordinator::new),
    GARMIN_EDGE_850(GarminEdge850Coordinator::new),
    GARMIN_EDGE_1000(GarminEdge1000Coordinator::new),
    GARMIN_EDGE_1030(GarminEdge1030Coordinator::new),
    GARMIN_EDGE_1030_PLUS(GarminEdge1030PlusCoordinator::new),
    GARMIN_EDGE_1040(GarminEdge1040Coordinator::new),
    GARMIN_EDGE_1050(GarminEdge1050Coordinator::new),
    GARMIN_EDGE_EXPLORE(GarminEdgeExploreCoordinator::new),
    GARMIN_EDGE_EXPLORE_2(GarminEdgeExplore2Coordinator::new),
    GARMIN_EDGE_EXPLORE_820(GarminEdgeExplore820Coordinator::new),
    GARMIN_EDGE_EXPLORE_1000(GarminEdgeExplore1000Coordinator::new),
    GARMIN_EDGE_MTB(GarminEdgeMtbCoordinator::new),
    GARMIN_GPSMAP_66I(GarminGpsmap66iCoordinator::new),
    GARMIN_GPSMAP_66S(GarminGpsmap66sCoordinator::new),
    GARMIN_GPSMAP_66SR(GarminGpsmap66srCoordinator::new),
    GARMIN_GPSMAP_H1(GarminGpsmapH1Coordinator::new),
    GARMIN_ETREX_SE(GarminETrexSeCoordinator::new),
    GARMIN_ENDURO(GarminEnduroCoordinator::new),
    GARMIN_ENDURO_2(GarminEnduro2Coordinator::new),
    GARMIN_ENDURO_3(GarminEnduro3Coordinator::new),
    GARMIN_ENDURO_4(GarminEnduro4Coordinator::new),
    GARMIN_EPIX(GarminEpixCoordinator::new),
    GARMIN_EPIX_GEN2(GarminEpixGen2Coordinator::new),
    GARMIN_EPIX_PRO(GarminEpixProCoordinator::new),
    GARMIN_DESCENT_MK3(GarminDescentMk3Coordinator::new),
    GARMIN_DESCENT_MK3I(GarminDescentMk3iCoordinator::new),
    GARMIN_DESCENT_G1(GarminDescentG1Coordinator::new),
    GARMIN_DESCENT_G2(GarminDescentG2Coordinator::new),
    GARMIN_FENIX_2(GarminFenix2Coordinator::new),
    GARMIN_FENIX_3(GarminFenix3Coordinator::new),
    GARMIN_FENIX_3_CHRONOS(GarminFenix3ChronosCoordinator::new),
    GARMIN_FENIX_3_HR(GarminFenix3HrCoordinator::new),
    GARMIN_FENIX_5(GarminFenix5Coordinator::new),
    GARMIN_FENIX_5S(GarminFenix5SCoordinator::new),
    GARMIN_FENIX_5S_PLUS(GarminFenix5SPlusCoordinator::new),
    GARMIN_FENIX_5_PLUS(GarminFenix5PlusCoordinator::new),
    GARMIN_FENIX_5X(GarminFenix5XCoordinator::new),
    GARMIN_FENIX_5X_PLUS(GarminFenix5XPlusCoordinator::new),
    GARMIN_FENIX_6(GarminFenix6Coordinator::new),
    GARMIN_FENIX_6_SPORT(GarminFenix6SportCoordinator::new),
    GARMIN_FENIX_6_PRO(GarminFenix6ProCoordinator::new),
    GARMIN_FENIX_6_SAPPHIRE(GarminFenix6SapphireCoordinator::new),
    GARMIN_FENIX_6S_PRO(GarminFenix6SProCoordinator::new),
    GARMIN_FENIX_6S(GarminFenix6SCoordinator::new),
    GARMIN_FENIX_6S_SPORT(GarminFenix6SSportCoordinator::new),
    GARMIN_FENIX_6S_PRO_SOLAR(GarminFenix6SProSolarCoordinator::new),
    GARMIN_FENIX_6S_SAPPHIRE(GarminFenix6SSapphireCoordinator::new),
    GARMIN_FENIX_6X(GarminFenix6XCoordinator::new),
    GARMIN_FENIX_6X_PRO(GarminFenix6XProCoordinator::new),
    GARMIN_FENIX_6X_PRO_SOLAR(GarminFenix6XProSolarCoordinator::new),
    GARMIN_FENIX_6X_SAPPHIRE(GarminFenix6XSapphireCoordinator::new),
    GARMIN_FENIX_7(GarminFenix7Coordinator::new),
    GARMIN_FENIX_7S(GarminFenix7SCoordinator::new),
    GARMIN_FENIX_7S_PRO(GarminFenix7SProCoordinator::new),
    GARMIN_FENIX_7S_PRO_SOLAR(GarminFenix7SProSolarCoordinator::new),
    GARMIN_FENIX_7X(GarminFenix7XCoordinator::new),
    GARMIN_FENIX_7_PRO(GarminFenix7ProCoordinator::new),
    GARMIN_FENIX_7_PRO_SOLAR(GarminFenix7ProSolarCoordinator::new),
    GARMIN_FENIX_7X_PRO(GarminFenix7XProCoordinator::new),
    GARMIN_FENIX_7X_PRO_SOLAR(GarminFenix7XProSolarCoordinator::new),
    GARMIN_FENIX_8(GarminFenix8Coordinator::new),
    GARMIN_FENIX_8_SOLAR(GarminFenix8SolarCoordinator::new),
    GARMIN_FENIX_8_PRO(GarminFenix8ProCoordinator::new),
    GARMIN_FENIX_9(GarminFenix9Coordinator::new),
    GARMIN_FENIX_9_PRO(GarminFenix9ProCoordinator::new),
    GARMIN_FENIX_9_PRO_SOLAR(GarminFenix9ProSolarCoordinator::new),
    GARMIN_FENIX_E(GarminFenixECoordinator::new),
    GARMIN_FORERUNNER_25(GarminForerunner25Coordinator::new),
    GARMIN_FORERUNNER_30(GarminForerunner30Coordinator::new),
    GARMIN_FORERUNNER_35(GarminForerunner35Coordinator::new),
    GARMIN_FORERUNNER_45(GarminForerunner45Coordinator::new),
    GARMIN_FORERUNNER_45_PLUS(GarminForerunner45PlusCoordinator::new),
    GARMIN_FORERUNNER_55(GarminForerunner55Coordinator::new),
    GARMIN_FORERUNNER_165(GarminForerunner165Coordinator::new),
    GARMIN_FORERUNNER_165_MUSIC(GarminForerunner165MusicCoordinator::new),
    GARMIN_FORERUNNER_170(GarminForerunner170Coordinator::new),
    GARMIN_FORERUNNER_170_MUSIC(GarminForerunner170MusicCoordinator::new),
    GARMIN_FORERUNNER_220(GarminForerunner220Coordinator::new),
    GARMIN_FORERUNNER_225(GarminForerunner225Coordinator::new),
    GARMIN_FORERUNNER_230(GarminForerunner230Coordinator::new),
    GARMIN_FORERUNNER_235(GarminForerunner235Coordinator::new),
    GARMIN_FORERUNNER_245(GarminForerunner245Coordinator::new),
    GARMIN_FORERUNNER_245_MUSIC(GarminForerunner245MusicCoordinator::new),
    GARMIN_FORERUNNER_255(GarminForerunner255Coordinator::new),
    GARMIN_FORERUNNER_255_MUSIC(GarminForerunner255MusicCoordinator::new),
    GARMIN_FORERUNNER_255S(GarminForerunner255SCoordinator::new),
    GARMIN_FORERUNNER_255S_MUSIC(GarminForerunner255SMusicCoordinator::new),
    GARMIN_FORERUNNER_265(GarminForerunner265Coordinator::new),
    GARMIN_FORERUNNER_265S(GarminForerunner265SCoordinator::new),
    GARMIN_FORERUNNER_570(GarminForerunner570Coordinator::new),
    GARMIN_FORERUNNER_620(GarminForerunner620Coordinator::new),
    GARMIN_FORERUNNER_630(GarminForerunner630Coordinator::new),
    GARMIN_FORERUNNER_645(GarminForerunner645Coordinator::new),
    GARMIN_FORERUNNER_645_MUSIC(GarminForerunner645MusicCoordinator::new),
    GARMIN_FORERUNNER_70(GarminForerunner70Coordinator::new),
    GARMIN_FORERUNNER_735XT(GarminForerunner735XTCoordinator::new),
    GARMIN_FORERUNNER_745(GarminForerunner745Coordinator::new),
    GARMIN_FORERUNNER_920(GarminForerunner920Coordinator::new),
    GARMIN_FORERUNNER_920XT(GarminForerunner920XTCoordinator::new),
    GARMIN_FORERUNNER_935(GarminForerunner935Coordinator::new),
    GARMIN_FORERUNNER_945(GarminForerunner945Coordinator::new),
    GARMIN_FORERUNNER_945_LTE(GarminForerunner945LTECoordinator::new),
    GARMIN_FORERUNNER_955(GarminForerunner955Coordinator::new),
    GARMIN_FORERUNNER_965(GarminForerunner965Coordinator::new),
    GARMIN_FORERUNNER_970(GarminForerunner970Coordinator::new),
    GARMIN_QUATIX_6(GarminQuatix6Coordinator::new),
    GARMIN_QUATIX_6X(GarminQuatix6XCoordinator::new),
    GARMIN_QUATIX_7(GarminQuatix7Coordinator::new),
    GARMIN_QUATIX_7X(GarminQuatix7XCoordinator::new),
    GARMIN_QUATIX_8(GarminQuatix8Coordinator::new),
    GARMIN_TACTIX_7(GarminTactix7Coordinator::new),
    GARMIN_TACTIX_8(GarminTactix8Coordinator::new),
    GARMIN_TACTIX_8_SOLAR(GarminTactix8SolarCoordinator::new),
    GARMIN_SWIM_2(GarminSwim2Coordinator::new),
    GARMIN_INREACH_MINI_2(GarminInReachMini2::new),
    GARMIN_INSTINCT(GarminInstinctCoordinator::new),
    GARMIN_INSTINCT_SOLAR(GarminInstinctSolarCoordinator::new),
    GARMIN_INSTINCT_TACTICAL(GarminInstinctTacticalCoordinator::new),
    GARMIN_INSTINCT_SOLAR_TACTICAL(GarminInstinctSolarTacticalCoordinator::new),
    GARMIN_INSTINCT_2(GarminInstinct2Coordinator::new),
    GARMIN_INSTINCT_2S(GarminInstinct2SCoordinator::new),
    GARMIN_INSTINCT_2S_SOLAR(GarminInstinct2SSolarCoordinator::new),
    GARMIN_INSTINCT_2S_SURF(GarminInstinct2SSurfCoordinator::new),
    GARMIN_INSTINCT_2X(GarminInstinct2XCoordinator::new),
    GARMIN_INSTINCT_2X_SOLAR(GarminInstinct2XSolarCoordinator::new),
    GARMIN_INSTINCT_2X_TACTICAL(GarminInstinct2XTacticalCoordinator::new),
    GARMIN_INSTINCT_2_SOLAR(GarminInstinct2SolarCoordinator::new),
    GARMIN_INSTINCT_2_SOLTAC(GarminInstinct2SolTacCoordinator::new),
    GARMIN_INSTINCT_CROSSOVER(GarminInstinctCrossoverCoordinator::new),
    GARMIN_INSTINCT_E(GarminInstinctECoordinator::new),
    GARMIN_INSTINCT_3(GarminInstinct3Coordinator::new),
    GARMIN_INSTINCT_3_SOLAR(GarminInstinct3SolarCoordinator::new),
    GARMIN_INSTINCT_3_SOLAR_TACTICAL(GarminInstinct3SolarTacticalCoordinator::new),
    GARMIN_INSTINCT_3_TACTICAL(GarminInstinct3TacticalCoordinator::new),
    GARMIN_LILY(GarminLilyCoordinator::new),
    GARMIN_LILY_2(GarminLily2Coordinator::new),
    GARMIN_LILY_2_ACTIVE(GarminLily2ActiveCoordinator::new),
    GARMIN_VIVOMOVE(GarminVivomoveCoordinator::new),
    GARMIN_VIVOMOVE_SPORT(GarminVivomoveSportCoordinator::new),
    GARMIN_VIVOMOVE_STYLE(GarminVivomoveStyleCoordinator::new),
    GARMIN_VIVOMOVE_TREND(GarminVivomoveTrendCoordinator::new),
    GARMIN_VENU(GarminVenuCoordinator::new),
    GARMIN_VENU_SQ(GarminVenuSqCoordinator::new),
    GARMIN_VENU_SQ_MUSIC(GarminVenuSqMusicCoordinator::new),
    GARMIN_VENU_SQ_2(GarminVenuSq2Coordinator::new),
    GARMIN_VENU_SQ_2_MUSIC(GarminVenuSq2MusicCoordinator::new),
    GARMIN_VENU_2(GarminVenu2Coordinator::new),
    GARMIN_VENU_2S(GarminVenu2SCoordinator::new),
    GARMIN_VENU_2_PLUS(GarminVenu2PlusCoordinator::new),
    GARMIN_VENU_3(GarminVenu3Coordinator::new),
    GARMIN_VENU_3S(GarminVenu3SCoordinator::new),
    GARMIN_VENU_4(GarminVenu4Coordinator::new),
    GARMIN_VENU_X1(GarminVenuX1Coordinator::new),
    GARMIN_VIVOACTIVE(GarminVivoActiveCoordinator::new),
    GARMIN_VIVOACTIVE_3(GarminVivoActive3Coordinator::new),
    GARMIN_VIVOACTIVE_3_MUSIC(GarminVivoActive3MusicCoordinator::new),
    GARMIN_VIVOACTIVE_3T(GarminVivoActive3TCoordinator::new),
    GARMIN_VIVOACTIVE_4(GarminVivoActive4Coordinator::new),
    GARMIN_VIVOACTIVE_4S(GarminVivoActive4SCoordinator::new),
    GARMIN_VIVOACTIVE_5(GarminVivoActive5Coordinator::new),
    GARMIN_VIVOACTIVE_6(GarminVivoActive6Coordinator::new),
    GARMIN_VIVOACTIVE_HR(GarminVivoActiveHrCoordinator::new),
    GARMIN_VIVOSMART(GarminVivosmartCoordinator::new),
    GARMIN_VIVOSMART_HR(GarminVivosmartHrCoordinator::new),
    GARMIN_VIVOSMART_HR_PLUS(GarminVivosmartHrPlusCoordinator::new),
    GARMIN_VIVOSMART_3(GarminVivosmart3Coordinator::new),
    GARMIN_VIVOSMART_4(GarminVivosmart4Coordinator::new),
    GARMIN_VIVOSMART_5(GarminVivosmart5Coordinator::new),
    GARMIN_VIVOSPORT(GarminVivosportCoordinator::new),
    GARMIN_CIRQA(GarminCirqaCoordinator::new),
    GARMIN_D2_AIR(GarminD2AirCoordinator::new),
    GARMIN_D2_AIR_X10(GarminD2AirX10Coordinator::new),
    GARMIN_D2_AIR_X15(GarminD2AirX15Coordinator::new),
    GARMIN_D2_MACH_1(GarminD2Mach1Coordinator::new),
    GARMIN_D2_MACH_1_PRO(GarminD2Mach1ProCoordinator::new),
    GARMIN_D2_MACH_2(GarminD2Mach2Coordinator::new),
    GARMIN_D2_MACH_2_PRO(GarminD2Mach2ProCoordinator::new),
    GREE_AC(GreeAcCoordinator::new),
    AAWIRELESS(AAWirelessCoordinator::new),
    VIBRATISSIMO(VibratissimoCoordinator::new),
    SONY_SWR12(SonySWR12DeviceCoordinator::new),
    LIVEVIEW(LiveviewCoordinator::new),
    WASPOS(WaspOSCoordinator::new),
    UM25(UM25Coordinator::new),
    DOMYOS_T540(DomyosT540Coordinator::new),
    CONCEPT2_PM5(Concept2PM5Coordinator::new),
    EVEN_REALITIES_G_1(G1DeviceCoordinator::new),
    NOTHING_EAR1(Ear1Coordinator::new),
    NOTHING_EAR2(Ear2Coordinator::new),
    NOTHING_EAR_A(EarACoordinator::new),
    NOTHING_EAR_STICK(EarStickCoordinator::new),
    NOTHING_CMF_BUDS_2(CmfBuds2Coordinator::new),
    NOTHING_CMF_BUDS_2A(CmfBuds2aCoordinator::new),
    NOTHING_CMF_BUDS_PRO_2(CmfBudsPro2Coordinator::new),
    NOTHING_CMF_BUDS_2_PLUS(CmfBuds2PlusCoordinator::new),
    NOTHING_CMF_WATCH_PRO(CmfWatchProCoordinator::new),
    NOTHING_CMF_WATCH_PRO_2(CmfWatchPro2Coordinator::new),
    NOTHING_CMF_WATCH_PRO_3(CmfWatchPro3Coordinator::new),
    GALAXY_BUDS_PRO(GalaxyBudsProDeviceCoordinator::new),
    GALAXY_BUDS_LIVE(GalaxyBudsLiveDeviceCoordinator::new),
    GALAXY_BUDS(GalaxyBudsDeviceCoordinator::new),
    GALAXY_BUDS2(GalaxyBuds2DeviceCoordinator::new),
    GALAXY_BUDS2_PRO(GalaxyBuds2ProDeviceCoordinator::new),
    GALAXY_BUDS3_PRO(GalaxyBuds3ProDeviceCoordinator::new),
    SONY_WH_1000XM3(SonyWH1000XM3Coordinator::new),
    SONY_WF_SP800N(SonyWFSP800NCoordinator::new),
    SONY_WI_C100(SonyWIC100Coordinator::new),
    SONY_WI_SP600N(SonyWISP600NCoordinator::new),
    SONY_WH_1000XM4(SonyWH1000XM4Coordinator::new),
    SONY_WF_1000XM3(SonyWF1000XM3Coordinator::new),
    SONY_WH_1000XM2(SonyWH1000XM2Coordinator::new),
    SONY_WF_1000XM4(SonyWF1000XM4Coordinator::new),
    SONY_LINKBUDS(SonyLinkBudsCoordinator::new),
    SONY_LINKBUDS_S(SonyLinkBudsSCoordinator::new),
    SONY_WH_1000XM5(SonyWH1000XM5Coordinator::new),
    SONY_WH_1000XM6(SonyWH1000XM6Coordinator::new),
    SONY_WH_ULT900N(SonyWHULT900NCoordinator::new),
    SONY_WF_1000XM5(SonyWF1000XM5Coordinator::new),
    SONY_WF_C500(SonyWFC500Coordinator::new),
    SONY_WF_C510(SonyWFC510Coordinator::new),
    SONY_WF_C700N(SonyWFC700NCoordinator::new),
    SONY_WF_C710N(SonyWFC710NCoordinator::new),
    SONY_WH_CH720N(SonyWHCH720NCoordinator::new),
    SONY_REON_POCKET_PRO(SonyReonPocketProCoordinator::new),
    PIXEL_BUDS_A(PixelBudsACoordinator::new),
    SHOKZ_OPENSWIM_PRO(ShokzOpenSwimProCoordinator::new),
    SHOKZ_OPENRUN_PRO_2(ShokzOpenRunPro2Coordinator::new),
    BM2_BATTERY_MONITOR(Bm2Coordinator::new),
    BM6_BATTERY_MONITOR(Bm6Coordinator::new),
    VICTRON_SMARTSHUNT(VictronSmartShuntCoordinator::new),
    DJI_OSMO_ACTION_4(DjiOsmoAction4Coordinator::new),
    SINILINK(SinilinkCoordinator::new),
    ONETOUCH(OneTouchCoordinator::new),
    SOUNDBRENNER_CORE(SoundbrennerCoordinator::new),
    SOUNDCORE_LIBERTY3_PRO(SoundcoreLiberty3ProCoordinator::new),
    SOUNDCORE_LIBERTY4_NC(SoundcoreLiberty4NCCoordinator::new),
    SOUNDCORE_MOTION300(SoundcoreMotion300Coordinator::new),
    SOUNDCORE_MOTION_PLUS(SoundcoreMotionPlusCoordinator::new),
    SOUNDCORE_Q30(SoundcoreQ30Coordinator::new),
    SOUNDCORE_SPACE_ONE(SoundcoreSpaceOneCoordinator::new),
    SOUNDCORE_LIFE_TUNE(SoundcoreLifeTuneCoordinator::new),
    SOUNDCORE_LIFE_TUNE_XR(SoundcoreLifeTuneXRCoordinator::new),
    SOUNDCORE_AEROFIT2(SoundcoreAeroFit2Coordinator::new),
    SOUNDCORE_SPORT_X20(SoundcoreSportX20Coordinator::new),
    SOUNDCORE_R50I_NC(SoundcoreR50iNCCoordinator::new),
    SOUNDCORE_A1(SoundcoreA1Coordinator::new),
    SOUNDCORE_A2_NC(SoundcoreA2NCCoordinator::new),
    SOUNDCORE_A3I(SoundcoreA3iCoordinator::new),
    SOUNDCORE_A30I(SoundcoreA30iCoordinator::new),
    SOUNDCORE_K20I(SoundcoreK20iCoordinator::new),
    SOUNDCORE_P2(SoundcoreP2Coordinator::new),
    SOUNDCORE_P2I(SoundcoreP2iCoordinator::new),
    SOUNDCORE_P2_MINI(SoundcoreP2MiniCoordinator::new),
    SOUNDCORE_P3(SoundcoreP3Coordinator::new),
    SOUNDCORE_P3I(SoundcoreP3iCoordinator::new),
    SOUNDCORE_P20I(SoundcoreP20iCoordinator::new),
    SOUNDCORE_P30I(SoundcoreP30iCoordinator::new),
    SOUNDCORE_P31I(SoundcoreP31iCoordinator::new),
    SOUNDCORE_P40I(SoundcoreP40iCoordinator::new),
    SOUNDCORE_P41I(SoundcoreP41iCoordinator::new),
    SOUNDCORE_P42I(SoundcoreP42iCoordinator::new),
    SOUNDCORE_V20I(SoundcoreV20iCoordinator::new),
    HAYLOU_S35_ANC(HaylouS35AncCoordinator::new),
    MOONDROP_SPACE_TRAVEL(MoondropSpaceTravelCoordinator::new),
    MOONDROP_SPACE_TRAVEL_2(MoondropSpaceTravel2Coordinator::new),
    MOONDROP_SPACE_TRAVEL_2_ULTRA(MoondropSpaceTravel2UltraCoordinator::new),
    BOSE_QC35(QC35Coordinator::new),
    BOSE_NC700(NC700Coordinator::new),
    JABRA_EVOLVE_65(JabraEvolve65Coordinator::new),
    JABRA_EVOLVE2_55(JabraEvolve255Coordinator::new),
    JABRA_EVOLVE2_65(JabraEvolve265Coordinator::new),
    ONEMORE_SONOFLOW(OneMoreSonoFlowCoordinator::new),
    ONEMORE_SONOFLOW_SE(OneMoreSonoFlowSECoordinator::new),
    UGREEN_HITUNE_MAX_5C(UgreenHituneMax5cCoordinator::new),
    HONORBAND3(HonorBand3Coordinator::new),
    HONORBAND4(HonorBand4Coordinator::new),
    HONORBAND5(HonorBand5Coordinator::new),
    HUAWEIBANDAW70(HuaweiBandAw70Coordinator::new),
    HUAWEIBANDA2(HuaweiBandA2Coordinator::new),
    HUAWEIBAND6(HuaweiBand6Coordinator::new),
    HUAWEIWATCHGT(HuaweiWatchGTCoordinator::new),
    HUAWEIBAND2PRO(HuaweiBand2ProCoordinator::new),
    HUAWEIBAND3PRO(HuaweiBand3ProCoordinator::new),
    HUAWEIBAND4PRO(HuaweiBand4ProCoordinator::new),
    HUAWEIWATCHGT2(HuaweiWatchGT2Coordinator::new),
    HUAWEIWATCHGT2E(HuaweiWatchGT2eCoordinator::new),
    HUAWEITALKBANDB6(HuaweiTalkBandB6Coordinator::new),
    HUAWEIBAND7(HuaweiBand7Coordinator::new),
    HONORBAND6(HonorBand6Coordinator::new),
    HONORBAND7(HonorBand7Coordinator::new),
    HONORBAND9(HonorBand9Coordinator::new),
    HONORBAND10(HonorBand10Coordinator::new),
    HONORWATCH4(HonorWatch4Coordinator::new),
    HONORWATCH5(HonorWatch5Coordinator::new),
    HONORWATCH5PRO(HonorWatch5ProCoordinator::new),
    HONORWATCH6(HonorWatch6Coordinator::new),
    HONORMAGICWATCH(HonorMagicWatchCoordinator::new),
    HONORMAGICWATCH2(HonorMagicWatch2Coordinator::new),
    HONORWATCHGS3(HonorWatchGS3Coordinator::new),
    HONORWATCHGSPRO(HonorWatchGSProCoordinator::new),
    HUAWEIWATCHD2(HuaweiWatchD2Coordinator::new),
    HUAWEIWATCHD3(HuaweiWatchD3Coordinator::new),
    HUAWEIWATCHGT3(HuaweiWatchGT3Coordinator::new),
    HUAWEIWATCHGT4(HuaweiWatchGT4Coordinator::new),
    HUAWEIWATCHGT5(HuaweiWatchGT5Coordinator::new),
    HUAWEIWATCHGT6(HuaweiWatchGT6Coordinator::new),
    HUAWEIWATCHGT7(HuaweiWatchGT7Coordinator::new),
    HUAWEIWATCHGT7PRO(HuaweiWatchGT7ProCoordinator::new),
    HUAWEIWATCHGTRUNNER(HuaweiWatchGTRunnerCoordinator::new),
    HUAWEIWATCHGTRUNNER2(HuaweiWatchGTRunner2Coordinator::new),
    HUAWEIWATCHGTCYBER(HuaweiWatchGTCyberCoordinator::new),
    HUAWEIBAND8(HuaweiBand8Coordinator::new),
    HUAWEIBAND9(HuaweiBand9Coordinator::new),
    HUAWEIBAND10(HuaweiBand10Coordinator::new),
    HUAWEIBAND11(HuaweiBand11Coordinator::new),
    HUAWEIBAND11PRO(HuaweiBand11ProCoordinator::new),
    HUAWEIWATCHFIT(HuaweiWatchFitCoordinator::new),
    HUAWEIWATCHFIT2(HuaweiWatchFit2Coordinator::new),
    HUAWEIWATCHFIT3(HuaweiWatchFit3Coordinator::new),
    HUAWEIWATCHFIT4PRO(HuaweiWatchFit4ProCoordinator::new),
    HUAWEIWATCHFIT5(HuaweiWatchFit5Coordinator::new),
    HUAWEIWATCHFIT5PRO(HuaweiWatchFit5ProCoordinator::new),
    HUAWEIWATCHULTIMATE(HuaweiWatchUltimateCoordinator::new),
    HUAWEIWATCHULTIMATE2(HuaweiWatchUltimate2Coordinator::new),
    HUAWEIWATCH3(HuaweiWatch3Coordinator::new),
    HUAWEIWATCH4PRO(HuaweiWatch4ProCoordinator::new),
    HUAWEIWATCH5(HuaweiWatch5Coordinator::new),
    HUAWEIWATCH6(HuaweiWatch6Coordinator::new),
    HUAWEIWATCH6PRO(HuaweiWatch6ProCoordinator::new),
    HUAWEI_FREEARC(HuaweiFreearcCoordinator::new),
    HUAWEI_FREECLIP2(HuaweiFreeClip2Coordinator::new),
    HUAWEI_FREEBUDS5I(HuaweiFreebuds5iCoordinator::new),
    HUAWEI_FREEBUDS_PRO(HuaweiFreebudsProCoordinator::new),
    HUAWEI_FREEBUDS6(HuaweiFreebuds6Coordinator::new),
    VESC(VescCoordinator::new),
    BINARY_SENSOR(BinarySensorCoordinator::new),
    FLIPPER_ZERO(FlipperZeroCoordinator::new),
    SUPER_CARS(SuperCarsCoordinator::new),
    ASTEROIDOS(AsteroidOSDeviceCoordinator::new),
    OPPO_ENCO_AIR(OppoEncoAirCoordinator::new),
    OPPO_ENCO_AIR2(OppoEncoAir2Coordinator::new),
    OPPO_ENCO_BUDS2(OppoEncoBuds2Coordinator::new),
    OUKITEL_BT103(OukitelBT103Coordinator::new),
    GLORYFIT_SWISS_PEAK(SwissPeakCoordinator::new),
    BLACKVIEW_R3MAX(BlackviewR3MaxCoordinator::new),
    DOTN_P66D(DotnP66DCoordinator::new),
    QECNATO_S10(QecnatoS10Coordinator::new),
    R1(R1Coordinator::new),
    GRV_FC1(GrvFc1Coordinator::new),
    IMIKI_FRAME_2(ImikiFrame2Coordinator::new),
    KIESLECT_CALLING_SMARTWATCH_KR_PRO(KieslectCallingSmartwatchKrProCoordinator::new),
    HAYLOU_WATCH_2_PRO(HaylouWatch2ProCoordinator::new),
    S52(S52Coordinator::new),
    HIFUTURE_ZONE2(HiFutureZone2Coordinator::new),
    D3(D3Coordinator::new),
    Y6(Y6Coordinator::new),
    Y66(Y66Coordinator::new),
    T70(T70Coordinator::new),
    DM58(DM58Coordinator::new),
    REALME_BUDS_T110(RealmeBudsT110Coordinator::new),
    REALME_BUDS_T100(RealmeBudsT100Coordinator::new),
    REALME_BUDS_T200(RealmeBudsT200Coordinator::new),
    REALME_BUDS_T300(RealmeBudsT300Coordinator::new),
    REALME_BUDS_AIR_5_PRO(RealmeBudsAir5ProCoordinator::new),
    REALME_BUDS_AIR_6_PRO(RealmeBudsAir6ProCoordinator::new),
    SOFLOW_SO6(SoFlowCoordinator::new),
    XIAOMI_SCOOTER_5_MAX(XiaomiScooter5MaxCoordinator::new),
    NINEBOT_KICKSCOOTER(NinebotScooterCoordinator::new),
    XIAOMI_HIPEE_P1(XiaomiHipeeP1Coordinator::new),
    WITHINGS_STEEL_HR(WithingsSteelHRDeviceCoordinator::new),
    SONY_WENA_3(SonyWena3Coordinator::new),
    FEMOMETER_VINCA2(FemometerVinca2DeviceCoordinator::new),
    PIXOO(PixooCoordinator::new),
    WEARFIT_HK8_PRO(WearFitCoordinator::new),
    HAMA_FIT6900(HamaFit6900DeviceCoordinator::new),
    XPLORA_XMOVE(XploraXmoveDeviceCoordinator::new),
    YAWELL_R05(YawellR05Coordinator::new),
    YAWELL_R10(YawellR10Coordinator::new),
    YAWELL_R11(YawellR11Coordinator::new),
    YAWELL_Y27(YawellY27Coordinator::new),
    COLMI_R02(ColmiR02Coordinator::new),
    COLMI_R03(ColmiR03Coordinator::new),
    COLMI_R06(ColmiR06Coordinator::new),
    COLMI_R07(ColmiR07Coordinator::new),
    COLMI_R09(ColmiR09Coordinator::new),
    COLMI_R10(ColmiR10Coordinator::new),
    COLMI_R12(ColmiR12Coordinator::new),
    H59(H59Coordinator::new),
    COLMI_I28_ULTRA(ColmiI28UltraCoordinator::new),
    COLMI_I31(ColmiI31Coordinator::new),
    COLMI_P10(ColmiP10Coordinator::new),
    COLMI_P76(ColmiP76Coordinator::new),
    COLMI_P80(ColmiP80Coordinator::new),
    COLMI_P81(ColmiP81Coordinator::new),
    COLMI_V72(ColmiV72Coordinator::new),
    COLMI_V73(ColmiV73Coordinator::new),
    COLMI_V76(ColmiV76Coordinator::new),
    COLMI_V89(ColmiV89Coordinator::new),
    ALT_VIBE(AltVibeCoordinator::new),
    BLACKVIEW_R60(BlackviewR60Coordinator::new),
    MISIRUN_C17(MisirunC17Coordinator::new),
    C20(C20Coordinator::new),
    C20PRO(C20ProCoordinator::new),
    L70(L70Coordinator::new),
    HC96(HC96Coordinator::new),
    KW19_B(KW19BCoordinator::new),
    KT80(KT80Coordinator::new),
    ITECH_ACTIVE_3(ITechActive3Coordinator::new),
    KSIX_VENTURE(KsixVentureCoordinator::new),
    FIREBOLTT_069(FireBoltt069Coordinator::new),
    FIREBOLTT_TALK(FireBolttTalkCoordinator::new),
    FIREBOLTT_NINJA_PHANTOM(FireBolttNinjaPhantomCoordinator::new),
    VIRAN_C29(ViranC29Coordinator::new),
    ZL02D(ZL02DCoordinator::new),
    R50PRO(R50ProCoordinator::new),
    RAINBUVVY_T97(RainbuvvyT97Coordinator::new),
    MT55(MT55Coordinator::new),
    GARETT_EVA(GarettEvaCoordinator::new),
    ADVAN_WATCH_SE1_AI(AdvanWatchSE1AICoordinator::new),
    BETTDOW_FB051(BettdowFB051Coordinator::new),
    B_AND_W_P_SERIES(BandWPSeriesDeviceCoordinator::new),
    EARFUN_AIR_S(EarFunAirSCoordinator::new),
    EARFUN_AIR_PRO_4(EarFunAirPro4Coordinator::new),
    EARFUN_FREE_PRO_3(EarFunFreePro3Coordinator::new),
    SENNHEISER_MOMENTUM_IN_EAR_WIRELESS(SennheiserMomentumInEarWirelessCoordinator::new),
    SCANNABLE(ScannableDeviceCoordinator::new),
    CYCLING_SENSOR(CyclingSensorCoordinator::new),
    BLE_GATT_CLIENT(BleGattClientCoordinator::new),
    MARSTEK_B2500(MarstekB2500DeviceCoordinator::new),
    GENERIC_HEADPHONES(GenericHeadphonesCoordinator::new),
    GENERIC_THERMAL_PRINTER(ThermalPrinterCoordinator::new),
    GENERIC_HEART_RATE(GenericHeartRateCoordinator::new),
    ULTRAHUMAN_RING_AIR(UltrahumanDeviceCoordinator::new),
    ATC_BLE_OEPL(ATCBLEOEPLCoordinator::new),
    COOSPO_H6(CoospoH6Coordinator::new),
    COOSPO_HW9(CoospoHW9Coordinator::new),
    COOSPO_HW807(CoospoHW807Coordinator::new),
    COOSPO_H9Z(CoospoH9ZCoordinator::new),
    COOSPO_H808S(CoospoH808SCoordinator::new),
    KEEPHEALTH_C60(C60DeviceCoordinator::new),
    GENERIC_WEIGHT_SCALE(GenericWeightScaleCoordinator::new),
    SOLARFLOW(SolarFlowDeviceCoordinator::new),
    SANITAS_SBM_67(SanitasSBM67Coordinator::new),
    SILVERCREST_SBM_67(SilverCrestSBM67Coordinator::new),
    BEURER_BM_59(BeurerBm59Coordinator::new),
    BEURER_BM_69(BeurerBm69Coordinator::new),
    BRAUN_BPW4500(BraunBPW4500DeviceCoordinator::new),
    TEUFEL_MYND(TeufelMyndCoordinator::new),
    IGPSPORT_BSC100MAX(IGPSportBSC100MaxCoordinator::new),
    IGPSPORT_BSC200(IGPSportBSC200Coordinator::new),
    IGPSPORT_BSC200S(IGPSportBSC200SCoordinator::new),
    IGPSPORT_BSC300(IGPSportBSC300Coordinator::new),
    IGPSPORT_BSC500(IGPSportBSC500Coordinator::new),
    IGPSPORT_IGS630(IGPSportiGS630Coordinator::new),
    IGPSPORT_IGS630S(IGPSportiGS630SCoordinator::new),
    IGPSPORT_IGS800(IGPSportiGS800Coordinator::new),
    IGPSPORT_BINAVI_AIR(IGPSportBiNaviAirCoordinator::new),
    IGPSPORT_BINAVI(IGPSportBiNaviCoordinator::new),
    OLLEE_WATCH_ONE(OlleeDeviceCoordinator::new),
    UNA_WATCH(UnaDeviceCoordinator::new),
    COROS_PACE_3(CorosPace3Coordinator::new),
    ZEBLAZE_BEYOND_3_PRO(Beyond3ProCoordinator::new),
    ZEBLAZE_STRATOS_4_PRO(Stratos4ProCoordinator::new),
    QN_SCALE(QnScaleCoordinator::new),
    CARDO_LS24X(Ls24xDeviceCoordinator::new),
    F8SCALE(F8ScaleCoordinator::new),
    EIGHTBITDO_MICRO(EightBitDoMicroCoordinator::new),
    INNOVO_IP900BP_B(InnovoPulseOximeterCoordinator::new),
    TEST(TestDeviceCoordinator::new);

    private DeviceCoordinator coordinator;

    private final CoordinatorBuilder coordinatorBuilder;

    DeviceType(CoordinatorBuilder coordinatorBuilder) {
        this.coordinatorBuilder = coordinatorBuilder;
    }

    private static interface CoordinatorBuilder {
        DeviceCoordinator build();
    }

    public boolean isSupported() {
        return this != UNKNOWN;
    }

    public static DeviceType fromName(final String name) {
        for (DeviceType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return DeviceType.UNKNOWN;
    }

    public Class<? extends DeviceCoordinator> getCoordinatorClass() {
        var coordinator = getDeviceCoordinator();
        return coordinator.getClass();
    }

    public DeviceCoordinator getDeviceCoordinator() {
        if (coordinator == null) {
            coordinator = coordinatorBuilder.build();
        }
        return coordinator;
    }
}
