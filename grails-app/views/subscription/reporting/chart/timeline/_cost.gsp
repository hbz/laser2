JSPC.app.reporting.current.chart.option = {
    dataset: {
        dimensions: ['id', 'name', 'valueCons', 'valueConsTax', 'annual', 'isCurrent'],
        source: [
            <% data.each{ it -> print "[${it[0]}, '${it[1]}', ${it[2]},  ${it[3]}, '${it[4]}', ${it[5]}]," } %>
        ]
    },
    grid:  {
        top: 60,
        right: '5%',
        bottom: 10,
        left: '5%',
        containLabel: true
    },
    legend: { top: 'top' },
    xAxis: {
        type: 'category',
        axisLabel: {
            formatter: function(id, index) {
                return JSPC.app.reporting.current.chart.option.dataset.source[ index ][ 4 ]
            }
        }
    },
    yAxis: {},
    toolbox: JSPC.app.reporting.helper.toolbox,
    tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter (params) {
            var str = params[0].data[4]
            for (var i=0; i<params.length; i++) {
                var ci = new Intl.NumberFormat(JSPC.vars.locale, { style: 'currency', currency: 'EUR' }).format(params[i].data[ params[i].seriesIndex + 2 ])
                str += JSPC.app.reporting.helper.tooltip.getEntry(params[i].marker, params[i].seriesName, ci)
            }
            return str
        }
    },
    series: [
        {
            name: '${labels.chart[0]}',
            color: JSPC.app.reporting.helper.series.color.green,
            type: 'bar',
            encode: {
                x: 'id',
                y: 'valueCons'
            },
            label: {
                show: true,
                position: 'top',
                formatter (params) {
                    var index = JSPC.app.reporting.current.chart.option.dataset.dimensions.indexOf('valueCons')
                    return new Intl.NumberFormat(JSPC.vars.locale, { style: 'currency', currency: 'EUR' }).format(params.data[ index ])
                }
            },
            itemStyle: {
                color: function(params) {
                    return JSPC.app.reporting.helper.series.bar.itemStyle.color('green', (params.data[5] == true))
                }
            }
        },
        {
            name: '${labels.chart[1]}',
            color: JSPC.app.reporting.helper.series.color.blue,
            type: 'bar',
            encode: {
                x: 'id',
                y: 'valueConsTax'
            },
            label: {
                show: true,
                position: 'top',
                formatter (params) {
                    var index = JSPC.app.reporting.current.chart.option.dataset.dimensions.indexOf('valueConsTax')
                    return new Intl.NumberFormat(JSPC.vars.locale, { style: 'currency', currency: 'EUR' }).format(params.data[ index ])
                }
            },
            itemStyle: {
                color: function(params) {
                    return JSPC.app.reporting.helper.series.bar.itemStyle.color('blue', (params.data[5] == true))
                }
            }
        }
    ]
};