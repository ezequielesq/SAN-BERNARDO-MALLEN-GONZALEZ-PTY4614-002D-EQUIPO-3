import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Footer } from '../../shared/footer/footer';
import { EmpleadoMisSolicitudes } from './empleado-mis-solicitudes';
import { EmpleadoSolicitudNueva } from './empleado-solicitud-nueva';

@Component({
  selector: 'app-empleado-pagina',
  imports: [EmpleadoSolicitudNueva, EmpleadoMisSolicitudes, Footer],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './empleado-pagina.html',
})
export class EmpleadoPagina {}
