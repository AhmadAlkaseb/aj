-- Keep long prose tables readable instead of giving an ID column a quarter page.
function Table(table)
  local count = #table.colspecs
  local first = ''
  local rows = table.head and (table.head.rows or table.head[2]) or {}
  if rows[1] then
    local cells = rows[1].cells or rows[1][2]
    first = pandoc.utils.stringify(cells[1].contents or cells[1][5])
  end
  local widths
  if count == 4 and first == 'ID' then widths = {0.08, 0.25, 0.35, 0.32}
  elseif count == 4 then widths = {0.29, 0.13, 0.19, 0.39}
  elseif count == 3 and first == 'Period' then widths = {0.19, 0.25, 0.56}
  elseif count == 3 then widths = {0.27, 0.40, 0.33}
  elseif count == 2 then widths = {0.30, 0.70}
  end
  if widths then
    for i = 1, count do table.colspecs[i][2] = widths[i] end
  end
  return table
end
