package com.smartrdp.backend.producto;

import com.smartrdp.backend.catalogo.Categoria;
import com.smartrdp.backend.catalogo.CategoriaRepository;
import com.smartrdp.backend.catalogo.UnidadMedida;
import com.smartrdp.backend.catalogo.UnidadMedidaRepository;
import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.producto.dto.ProductoRequest;
import com.smartrdp.backend.producto.dto.ProductoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;

    /** Código ya validado y normalizado: uno de los dos es null según la categoría. */
    private record Codigos(String ptv, String codigo) {}

    @Transactional
    public ProductoResponse crear(ProductoRequest request) {
        validarUmbrales(request);
        Categoria categoria = resolverCategoria(request.categoriaId(), null);
        Codigos codigos = validarCodigos(request, categoria, null);
        Producto producto = new Producto();
        aplicar(producto, request, categoria, codigos);
        return toResponse(productoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse editar(Long id, ProductoRequest request) {
        Producto producto = findOrThrow(id);
        validarUmbrales(request);
        Categoria categoria = resolverCategoria(request.categoriaId(), producto.getCategoria());
        Codigos codigos = validarCodigos(request, categoria, id);
        aplicar(producto, request, categoria, codigos);
        return toResponse(producto);
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(boolean soloActivos) {
        List<Producto> productos = soloActivos
                ? productoRepository.findByActivoTrue()
                : productoRepository.findAll();
        return productos.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public byte[] exportarXlsx(boolean soloActivos) {
        List<ProductoResponse> productos = listar(soloActivos);
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             var out = new java.io.ByteArrayOutputStream()) {
            var sheet = wb.createSheet("Productos");
            var header = sheet.createRow(0);
            String[] columnas = {"Código PTV", "Código", "Nombre", "Categoría", "Unidad", "Perecible",
                    "Stock mínimo", "Stock crítico", "Activo"};
            for (int i = 0; i < columnas.length; i++) header.createCell(i).setCellValue(columnas[i]);

            int fila = 1;
            for (ProductoResponse p : productos) {
                var row = sheet.createRow(fila++);
                row.createCell(0).setCellValue(texto(p.codigoPtv()));
                row.createCell(1).setCellValue(texto(p.codigo()));
                row.createCell(2).setCellValue(p.nombre());
                row.createCell(3).setCellValue(p.categoriaNombre());
                row.createCell(4).setCellValue(p.unidadMedidaNombre());
                row.createCell(5).setCellValue(p.esPerecible() ? "Sí" : "No");
                row.createCell(6).setCellValue(p.stockMinimo());
                row.createCell(7).setCellValue(p.stockCritico());
                row.createCell(8).setCellValue(p.activo() ? "Sí" : "No");
            }
            wb.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("No se pudo generar el XLSX de productos", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportarPdf(boolean soloActivos) {
        List<ProductoResponse> productos = listar(soloActivos);
        // ByteArrayOutputStream.close() no hace nada realmente, pero declara "throws
        // IOException" en su firma — por eso NO va en un try-with-resources junto al
        // resto (que solo puede lanzar DocumentException); envolverlo ahí sería un
        // error de compilación por una excepción chequeada que nunca ocurre en la práctica.
        var out = new java.io.ByteArrayOutputStream();
        try {
            var documento = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4.rotate());
            com.lowagie.text.pdf.PdfWriter.getInstance(documento, out);
            documento.open();
            documento.add(new com.lowagie.text.Paragraph("Productos — Smart RDP"));
            documento.add(new com.lowagie.text.Paragraph(" "));

            var tabla = new com.lowagie.text.pdf.PdfPTable(9);
            tabla.setWidthPercentage(100);
            for (String columna : new String[]{"Código PTV", "Código", "Nombre", "Categoría", "Unidad",
                    "Perecible", "Stock mínimo", "Stock crítico", "Activo"}) {
                tabla.addCell(columna);
            }
            for (ProductoResponse p : productos) {
                tabla.addCell(texto(p.codigoPtv()));
                tabla.addCell(texto(p.codigo()));
                tabla.addCell(p.nombre());
                tabla.addCell(p.categoriaNombre());
                tabla.addCell(p.unidadMedidaNombre());
                tabla.addCell(p.esPerecible() ? "Sí" : "No");
                tabla.addCell(String.valueOf(p.stockMinimo()));
                tabla.addCell(String.valueOf(p.stockCritico()));
                tabla.addCell(p.activo() ? "Sí" : "No");
            }
            documento.add(tabla);
            documento.close();
            return out.toByteArray();
        } catch (com.lowagie.text.DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF de productos", e);
        }
    }

    @Transactional
    public void deshabilitar(Long id) {
        findOrThrow(id).setActivo(false);
    }

    @Transactional
    public void reactivar(Long id) {
        findOrThrow(id).setActivo(true);
    }

    private void aplicar(Producto producto, ProductoRequest request, Categoria categoria, Codigos codigos) {
        producto.setCodigoPtv(codigos.ptv());
        producto.setCodigo(codigos.codigo());
        producto.setNombre(request.nombre().trim());
        producto.setCategoria(categoria);
        producto.setUnidadMedida(resolverUnidad(request.unidadMedidaId(), producto.getUnidadMedida()));
        producto.setEsPerecible(request.esPerecible());
        producto.setStockMinimo(request.stockMinimo());
        producto.setStockCritico(request.stockCritico());
        producto.setCostoUnitario(request.costoUnitario());
    }

    /**
     * Regla de la categoría: si requiere PTV, el producto lleva código PTV (letras y números) y no
     * código numérico; si no, lleva código numérico y no PTV. Cada código es único por separado.
     */
    private Codigos validarCodigos(ProductoRequest request, Categoria categoria, Long idActual) {
        String ptv = limpiar(request.codigoPtv());
        String codigo = limpiar(request.codigo());

        if (categoria.isRequierePtv()) {
            if (ptv == null) {
                throw new BusinessException("Ingresa el código PTV del producto.", "codigoPtv");
            }
            if (!ptv.matches("[A-Za-z0-9]+")) {
                throw new BusinessException("El código PTV solo admite letras y números.", "codigoPtv");
            }
            if (codigo != null) {
                throw new BusinessException(
                        "Los productos de esta categoría usan código PTV, no código numérico.", "codigo");
            }
            ptv = ptv.toUpperCase(Locale.ROOT);
            boolean repetido = idActual == null
                    ? productoRepository.existsByCodigoPtv(ptv)
                    : productoRepository.existsByCodigoPtvAndIdNot(ptv, idActual);
            if (repetido) throw codigoDuplicado(ptv, "codigoPtv");
        } else {
            if (codigo == null) {
                throw new BusinessException("Ingresa el código del producto.", "codigo");
            }
            if (!codigo.matches("\\d+")) {
                throw new BusinessException("El código solo admite números.", "codigo");
            }
            if (ptv != null) {
                throw new BusinessException(
                        "Los productos de esta categoría usan código numérico, no código PTV.", "codigoPtv");
            }
            boolean repetido = idActual == null
                    ? productoRepository.existsByCodigo(codigo)
                    : productoRepository.existsByCodigoAndIdNot(codigo, idActual);
            if (repetido) throw codigoDuplicado(codigo, "codigo");
        }
        return new Codigos(ptv, codigo);
    }

    private static String limpiar(String valor) {
        if (valor == null) return null;
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private void validarUmbrales(ProductoRequest request) {
        if (request.stockCritico() > request.stockMinimo()) {
            throw new BusinessException(
                    "El stock crítico debe ser menor o igual al stock mínimo.", "stockCritico");
        }
    }

    private Categoria resolverCategoria(Long id, Categoria actual) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", id));
        boolean esLaActual = actual != null && id.equals(actual.getId());
        if (!categoria.isActivo() && !esLaActual) {
            throw new BusinessException(
                    "La categoría '" + categoria.getNombre() + "' está desactivada.", "categoriaId");
        }
        return categoria;
    }

    private UnidadMedida resolverUnidad(Long id, UnidadMedida actual) {
        UnidadMedida unidad = unidadMedidaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", id));
        boolean esLaActual = actual != null && id.equals(actual.getId());
        if (!unidad.isActivo() && !esLaActual) {
            throw new BusinessException(
                    "La unidad de medida '" + unidad.getNombre() + "' está desactivada.", "unidadMedidaId");
        }
        return unidad;
    }

    private BusinessException codigoDuplicado(String valor, String campo) {
        return new BusinessException("Ya existe un producto con el código " + valor + ".", campo);
    }

    private Producto findOrThrow(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    private ProductoResponse toResponse(Producto p) {
        return new ProductoResponse(
                p.getId(),
                p.getCodigoPtv(),
                p.getCodigo(),
                p.getNombre(),
                p.getCategoria().getId(),
                p.getCategoria().getNombre(),
                p.getUnidadMedida().getId(),
                p.getUnidadMedida().getNombre(),
                p.isEsPerecible(),
                p.getStockMinimo(),
                p.getStockCritico(),
                p.getCostoUnitario(),
                p.isActivo());
    }
}
