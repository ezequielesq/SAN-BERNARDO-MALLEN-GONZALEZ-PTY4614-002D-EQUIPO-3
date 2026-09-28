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

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;

    @Transactional
    public ProductoResponse crear(ProductoRequest request) {
        validarUmbrales(request);
        String codigoPtv = request.codigoPtv().trim();
        if (productoRepository.existsByCodigoPtv(codigoPtv)) {
            throw ptvDuplicado(codigoPtv);
        }
        Producto producto = new Producto();
        aplicar(producto, request, codigoPtv);
        return toResponse(productoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse editar(Long id, ProductoRequest request) {
        Producto producto = findOrThrow(id);
        validarUmbrales(request);
        String codigoPtv = request.codigoPtv().trim();
        if (productoRepository.existsByCodigoPtvAndIdNot(codigoPtv, id)) {
            throw ptvDuplicado(codigoPtv);
        }
        aplicar(producto, request, codigoPtv);
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
            String[] columnas = {"Código PTV", "Nombre", "Categoría", "Unidad", "Perecible",
                    "Stock mínimo", "Stock crítico", "Activo"};
            for (int i = 0; i < columnas.length; i++) header.createCell(i).setCellValue(columnas[i]);

            int fila = 1;
            for (ProductoResponse p : productos) {
                var row = sheet.createRow(fila++);
                row.createCell(0).setCellValue(p.codigoPtv());
                row.createCell(1).setCellValue(p.nombre());
                row.createCell(2).setCellValue(p.categoriaNombre());
                row.createCell(3).setCellValue(p.unidadMedidaNombre());
                row.createCell(4).setCellValue(p.esPerecible() ? "Sí" : "No");
                row.createCell(5).setCellValue(p.stockMinimo());
                row.createCell(6).setCellValue(p.stockCritico());
                row.createCell(7).setCellValue(p.activo() ? "Sí" : "No");
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

            var tabla = new com.lowagie.text.pdf.PdfPTable(8);
            tabla.setWidthPercentage(100);
            for (String columna : new String[]{"Código PTV", "Nombre", "Categoría", "Unidad",
                    "Perecible", "Stock mínimo", "Stock crítico", "Activo"}) {
                tabla.addCell(columna);
            }
            for (ProductoResponse p : productos) {
                tabla.addCell(p.codigoPtv());
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

    private void aplicar(Producto producto, ProductoRequest request, String codigoPtv) {
        producto.setCodigoPtv(codigoPtv);
        producto.setNombre(request.nombre().trim());
        producto.setCategoria(resolverCategoria(request.categoriaId(), producto.getCategoria()));
        producto.setUnidadMedida(resolverUnidad(request.unidadMedidaId(), producto.getUnidadMedida()));
        producto.setEsPerecible(request.esPerecible());
        producto.setStockMinimo(request.stockMinimo());
        producto.setStockCritico(request.stockCritico());
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

    private BusinessException ptvDuplicado(String codigoPtv) {
        return new BusinessException("Ya existe un producto con el código " + codigoPtv + ".", "codigoPtv");
    }

    private Producto findOrThrow(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    private ProductoResponse toResponse(Producto p) {
        return new ProductoResponse(
                p.getId(),
                p.getCodigoPtv(),
                p.getNombre(),
                p.getCategoria().getId(),
                p.getCategoria().getNombre(),
                p.getUnidadMedida().getId(),
                p.getUnidadMedida().getNombre(),
                p.isEsPerecible(),
                p.getStockMinimo(),
                p.getStockCritico(),
                p.isActivo());
    }
}
